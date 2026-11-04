package ch.threema.app.services.notification

import androidx.core.app.Person
import ch.threema.app.emojis.EmojiMarkupUtil
import ch.threema.app.services.MessageService.MessageString
import ch.threema.app.services.notification.NotificationService.FetchCacheUri
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.MessageType
import java.time.Instant

class ConversationMessageNotification(
    messageId: Int,
    messageUid: MessageUid,
    conversationHandle: NotificationConversationHandle,
    createdAt: Instant,
    thumbnailMimeType: String?,
    fetchThumbnailUri: FetchCacheUri?,
    senderPerson: Person?,
    messageString: MessageString,
    val messageType: MessageType?,
    val isMessageEdited: Boolean,
    val isMessageDeleted: Boolean,
) : ConversationNotification(
    messageId = messageId,
    messageUid = messageUid,
    conversationHandle = conversationHandle,
    createdAt = createdAt,
    thumbnailMimeType = thumbnailMimeType,
    fetchThumbnailUri = fetchThumbnailUri,
    senderPerson = senderPerson,
) {

    val message: CharSequence =
        messageString
            .message
            ?.let(EmojiMarkupUtil.getInstance()::addTextSpans)
            ?: ""

    val rawMessage: CharSequence =
        messageString
            .rawMessage
            ?: ""

    override val shouldApplySenderPerson: Boolean
        get() = message.isNotBlank()
}
