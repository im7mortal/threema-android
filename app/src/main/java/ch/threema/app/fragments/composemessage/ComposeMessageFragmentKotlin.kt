package ch.threema.app.fragments.composemessage

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import ch.threema.android.dismissDialog
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.domain.types.Identity
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ComposeMessageFragmentKotlin")

abstract class ComposeMessageFragmentKotlin : Fragment() {

    private val emptyOrDeleteConversationsUseCase: EmptyOrDeleteConversationsUseCase by inject()
    private val globalEventBuses: GlobalEventBuses by inject()

    @JvmField
    protected var conversationId: ConversationId? = null

    protected fun emptyConversation() {
        val conversationId = conversationId
        if (conversationId == null) {
            logger.warn("Cannot empty conversation, conversationId is null.")
            return
        }
        logger.info("Emptying conversation {}", conversationId)

        lifecycleScope.launch {
            GenericProgressDialog.newInstance(R.string.emptying_chat, R.string.emptying_chat_deleting_messages)
                .show(childFragmentManager, DIALOG_TAG_EMPTYING_CONVERSATION)
            val result = emptyOrDeleteConversationsUseCase.call(
                conversationIds = listOf(conversationId),
                mode = EmptyOrDeleteConversationsUseCase.Mode.EMPTY,
            )
            childFragmentManager.dismissDialog(DIALOG_TAG_EMPTYING_CONVERSATION)
            if (result is EmptyOrDeleteConversationsUseCase.Result.Completed && result.successCount == 1) {
                onConversationEmptied()
            } else {
                logger.error("Emptying conversation failed: {}", result)
                showToast(R.string.an_error_occurred)
            }
        }
    }

    abstract fun onConversationEmptied()

    protected fun deleteConversation() {
        val conversationId = conversationId
        if (conversationId == null) {
            logger.warn("Cannot delete conversation, conversationId is null")
            return
        }
        logger.info("Deleting conversation {}", conversationId)

        lifecycleScope.launch {
            GenericProgressDialog.newInstance(R.string.deleting_chat, R.string.emptying_chat_deleting_messages)
                .show(childFragmentManager, DIALOG_TAG_DELETING_CONVERSATION)
            val result = emptyOrDeleteConversationsUseCase.call(
                conversationIds = listOf(conversationId),
                mode = EmptyOrDeleteConversationsUseCase.Mode.DELETE,
            )
            childFragmentManager.dismissDialog(DIALOG_TAG_DELETING_CONVERSATION)
            if (result is EmptyOrDeleteConversationsUseCase.Result.Completed && result.successCount == 1) {
                globalEventBuses.conversations.emit(ConversationEvent.AllConversationsUpdated)
                if (conversationId is ContactConversationId) {
                    globalEventBuses.contacts.emit(ContactEvent.ContactUpdated(Identity(conversationId.identity)))
                }
                finishActivity()
            } else {
                logger.error("Deleting conversation failed: {}", result)
                showToast(R.string.an_error_occurred)
            }
        }
    }

    protected fun finishActivity() {
        activity?.finish()
    }

    companion object {
        private const val DIALOG_TAG_EMPTYING_CONVERSATION = "ec"
        private const val DIALOG_TAG_DELETING_CONVERSATION = "dc"
    }
}
