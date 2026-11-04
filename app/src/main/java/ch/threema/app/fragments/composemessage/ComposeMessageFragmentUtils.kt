package ch.threema.app.fragments.composemessage

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import ch.threema.app.R
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.emojis.EmojiUtil
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.MessageService
import ch.threema.app.services.UserService
import ch.threema.app.ui.LongToast
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.NameUtil
import ch.threema.app.wallpapers.WallpaperService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.supportsEmojiReactions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.InjectedParam

private val logger = getThreemaLogger("ComposeMessageFragmentUtils")

/**
 * This helper class serves as an extension to [ComposeMessageFragment], primarily to allow writing parts of it in Kotlin
 * and to ease a future rewrite.
 */
class ComposeMessageFragmentUtils(
    private val appContext: Context,
    private val messageService: MessageService,
    private val userService: UserService,
    private val contactService: ContactService,
    private val preferenceService: PreferenceService,
    private val wallpaperService: WallpaperService,
    private val emojiReactionsRepository: EmojiReactionsRepository,
    private val dispatcherProvider: DispatcherProvider,
    @InjectedParam
    private val fragment: ComposeMessageFragment,
    @InjectedParam
    private val receiver: MessageReceiver<*>,
    @InjectedParam
    private val isGroupChat: Boolean,
) {
    @UiThread
    fun onEmojiReactionClicked(
        emojiSequence: String?,
        messageModel: MessageModel?,
    ) {
        if (emojiSequence == null || messageModel == null) {
            logger.debug("messageModel or emojiSequence is null")
            return
        }

        if (emojiSequence == EmojiUtil.REPLACEMENT_CHARACTER) {
            logger.info("Unknown emoji reaction sequence clicked")
            LongToast.makeText(fragment.requireContext(), R.string.reaction_cannot_be_displayed, Toast.LENGTH_LONG).show()
        } else {
            if (!messageModel.supportsEmojiReactions()) {
                return
            }
            fragment.lifecycleScope.launch(dispatcherProvider.worker) {
                try {
                    if (receiver.getEmojiReactionSupport() == MessageReceiver.Reactions_NONE && isWithdraw(messageModel, emojiSequence)) {
                        showImpossibleWithdrawErrorDialog(messageModel)
                    } else if (!messageService.sendEmojiReaction(messageModel, emojiSequence, receiver, false)) {
                        showErrorDialogOnSendEmojiReactionFailed(messageModel)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to send emoji reaction", e)
                }
            }
        }
    }

    private fun isWithdraw(messageModel: MessageModel, emojiSequence: String): Boolean {
        val userIdentity = userService.getIdentity()
        return emojiReactionsRepository.safeGetReactionsByMessage(messageModel)
            .any { reaction -> reaction.senderIdentity == userIdentity && reaction.emojiSequence == emojiSequence }
    }

    @AnyThread
    private suspend fun showImpossibleWithdrawErrorDialog(messageModel: MessageModel) {
        showErrorDialog(
            title = R.string.emoji_reactions_cannot_remove_title,
            errorText = getImpossibleWithdrawErrorText(messageModel)
                ?: return,
        )
    }

    private fun getImpossibleWithdrawErrorText(messageModel: MessageModel): String? {
        // Phase 2 reaction support: Withdraw only possible if receiver supports reactions.
        return if (messageModel is GroupMessageModel) {
            logger.info("Cannot withdraw reaction in group without reaction support.")
            appContext.getString(R.string.emoji_reactions_cannot_remove_group_body)
        } else {
            logger.info("Cannot withdraw reaction, because chat partner does not support reactions yet.")
            NameUtil.getContactDisplayNameOrNickname(messageModel, contactService, userService, preferenceService.getContactNameFormat())
                ?.let { name ->
                    appContext.getString(R.string.emoji_reactions_cannot_remove_body, name)
                }
        }
    }

    @AnyThread
    private suspend fun showErrorDialogOnSendEmojiReactionFailed(messageModel: MessageModel) {
        showErrorDialog(
            title = R.string.emoji_reactions_unavailable_title,
            errorText = getReactionSendFailedErrorText(messageModel)
                ?: return,
        )
    }

    private fun getReactionSendFailedErrorText(messageModel: MessageModel): String? {
        // Phase 2 reaction supported by this client. Therefore an error means the receiver
        // does not support reactions.
        return if (isGroupChat) {
            // The group members can change so that no other group members support
            // reactions anymore. In this group it is not possible to send reactions anymore
            // but it is possible to still attempt sending a reaction by tapping a reaction
            // that is already present in the chat.
            appContext.getString(R.string.emoji_reactions_unavailable_group_body)
        } else {
            // If the contact does not support emoji reactions, the only way to send a reaction
            // is by tapping a reaction already present in the chat. This means, the chat partner
            // has previously supported reactions.
            // Thus, we conclude this error happened due to a client downgrade of the chat partner.
            logger.info("Emoji reactions seems to be unavailable due to a client downgrade of the chat partner.")
            NameUtil.getContactDisplayNameOrNickname(
                messageModel,
                contactService,
                userService,
                preferenceService.getContactNameFormat(),
            )
                ?.let { name ->
                    appContext.getString(R.string.emoji_reactions_unavailable_body, name)
                }
        }
    }

    @AnyThread
    private suspend fun showErrorDialog(@StringRes title: Int, errorText: String) = withContext(dispatcherProvider.main) {
        SimpleStringAlertDialog.newInstance(title, errorText)
            .show(fragment.parentFragmentManager, "er")
    }

    @UiThread
    fun setBackgroundWallpaper(
        conversationId: ConversationId,
        wallpaperView: ImageView,
    ) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bitmap: Bitmap? = wallpaperService.getWallpaperBitmap(
                    conversationId = conversationId,
                    landscape = ConfigUtils.isLandscape(appContext),
                    isDarkTheme = ConfigUtils.isTheDarkSide(appContext),
                )
                if (bitmap != null) {
                    wallpaperView.setImageBitmap(bitmap)
                    wallpaperView.isVisible = true
                } else {
                    wallpaperView.background = null
                    wallpaperView.isVisible = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to load wallpaper", e)
            }
        }
    }
}
