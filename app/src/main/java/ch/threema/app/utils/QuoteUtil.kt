package ch.threema.app.utils

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import ch.threema.app.R
import ch.threema.app.cache.ThumbnailCache
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.app.services.MessageService
import ch.threema.app.services.UserService
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.takeUnlessEmpty
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.belongsToSameConversationAs
import ch.threema.storage.models.data.MessageContentsType
import java.util.regex.Pattern

private val logger = getThreemaLogger("QuoteUtil")

class QuoteUtil(
    private val context: Context,
    private val messageService: MessageService,
    private val userService: UserService,
    private val fileService: FileService,
    private val preferenceService: PreferenceService,
) {

    /**
     * Get all content to be displayed in a message containing a quote
     *
     * @param includeMessageModel If set to `true`, the quoted message model is included in the `QuoteContent`
     */
    @JvmOverloads
    fun getQuoteContent(
        messageModel: MessageModel,
        messageReceiver: MessageReceiver<*>,
        thumbnailCache: ThumbnailCache? = null,
        includeMessageModel: Boolean = false,
    ): QuoteContent? {
        val quotedMessageId = messageModel.quotedMessageId ?: return null
        val bodyText = messageModel.body ?: ""
        val quotedMessageModel = messageService.getMessageModelByApiMessageIdAndReceiver(quotedMessageId.toString(), messageReceiver)

        if (quotedMessageModel == null || quotedMessageModel.isDeleted) {
            return QuoteContent.createDeleted(
                quotedMessageId = quotedMessageId,
                quotedText = context.getString(R.string.quoted_message_deleted),
                bodyText = bodyText,
            )
        }

        if (!quotedMessageModel.belongsToSameConversationAs(messageModel)) {
            return QuoteContent.createDeleted(
                quotedMessageId = quotedMessageId,
                quotedText = context.getString(R.string.quote_not_found),
                bodyText = bodyText,
            )
        }

        val viewElement = MessageUtil.getViewElement(quotedMessageModel, preferenceService.getContactNameFormat())
        val identity = if (quotedMessageModel.isOutbox) {
            userService.getIdentity()!!
        } else {
            quotedMessageModel.identity!!
        }
        val quotedText = viewElement.text
            ?.takeUnlessEmpty()
            ?: viewElement.placeholder
            ?: ""

        val thumbnail: Bitmap? = if (quotedMessageModel.messageContentsType != MessageContentsType.VOICE_MESSAGE) {
            try {
                fileService.getMessageThumbnailBitmap(quotedMessageModel, thumbnailCache)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        return QuoteContent.create(
            identity = identity,
            quotedText = quotedText,
            bodyText = bodyText,
            quotedMessageId = quotedMessageId,
            quotedMessageModel = quotedMessageModel.takeIf { includeMessageModel },
            messageReceiver = messageReceiver,
            thumbnail = thumbnail,
            icon = viewElement.icon,
        )
    }

    companion object {

        private val QUOTE_MATCH_PATTERN = Pattern.compile("(?sm)\\A> quote #([0-9a-f]{16})\\n\\n(.*)")
        private const val QUOTE_SIGNATURE_LENGTH = 27

        /**
         * Get the body and quoted message id based on the text of a text message. Note that the message
         * id is null if the text does not contain a quote. If parsing the text fails, the passed text
         * is returned as body and the message id is null.
         *
         * @param text the text of a text message
         * @return a pair of the body and the quoted message id
         */
        fun getBodyAndQuotedMessageId(text: String?): Pair<String?, MessageId?> {
            try {
                text
                    .takeIf(::isQuote)
                    ?.let(QUOTE_MATCH_PATTERN::matcher)
                    ?.takeIf { it.find() && it.groupCount() == 2 }
                    ?.let { match ->
                        val quotedMessageId = match.group(1)
                            ?.let { messageIdString ->
                                try {
                                    MessageId.fromString(messageIdString)
                                } catch (_: ThreemaException) {
                                    null
                                }
                            }
                        val body = match.group(2) ?: ""
                        return Pair(body, quotedMessageId)
                    }
            } catch (e: Exception) {
                logger.error("Could not extract quote from text", e)
            }
            return Pair(text, null)
        }

        fun isQuote(body: String?): Boolean =
            body != null &&
                body.length >= QUOTE_SIGNATURE_LENGTH &&
                QUOTE_MATCH_PATTERN.matcher(body).matches()

        @JvmStatic
        fun createQuotedMessageBody(text: String, messageId: MessageId): String =
            "> quote #$messageId\n\n$text"
    }

    @ConsistentCopyVisibility
    data class QuoteContent private constructor(
        @JvmField val quotedText: String,
        @JvmField val bodyText: String,
        val quotedMessageId: MessageId,
        @JvmField
        val identity: IdentityString? = null,
        val quotedMessageModel: MessageModel? = null,
        val messageReceiver: MessageReceiver<*>? = null,
        @JvmField
        val thumbnail: Bitmap? = null,
        @JvmField
        @DrawableRes
        val icon: Int? = null,
    ) {
        companion object {
            @JvmStatic
            fun create(
                identity: IdentityString,
                quotedText: String,
                bodyText: String,
                quotedMessageId: MessageId,
                quotedMessageModel: MessageModel?,
                messageReceiver: MessageReceiver<*>?,
                thumbnail: Bitmap?,
                @DrawableRes icon: Int?,
            ): QuoteContent =
                QuoteContent(
                    quotedText = quotedText,
                    bodyText = bodyText,
                    quotedMessageId = quotedMessageId,
                    identity = identity,
                    quotedMessageModel = quotedMessageModel,
                    messageReceiver = messageReceiver,
                    thumbnail = thumbnail,
                    icon = icon,
                )

            fun createDeleted(
                quotedMessageId: MessageId,
                quotedText: String,
                bodyText: String,
            ): QuoteContent =
                QuoteContent(
                    quotedText = quotedText,
                    bodyText = bodyText,
                    quotedMessageId = quotedMessageId,
                )
        }
    }
}
