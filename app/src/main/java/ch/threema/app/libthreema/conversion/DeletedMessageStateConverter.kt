package ch.threema.app.libthreema.conversion

import ch.threema.libthreema.MessageStateBodyType
import ch.threema.storage.models.MessageType
import java.time.Instant

class DeletedMessageStateConverter {
    fun convertToMessageBodyAndRevisions(messageStateBodyType: MessageStateBodyType, deletedAt: Instant) = MessageBodyAndRevisions(
        messageType = messageStateBodyType.toMessageType(),
        originalBody = null,
        deletedAt = deletedAt,
    )

    private fun MessageStateBodyType.toMessageType() = when (this) {
        MessageStateBodyType.TEXT -> MessageType.TEXT
        MessageStateBodyType.LOCATION -> MessageType.LOCATION
        MessageStateBodyType.POLL_OPEN -> MessageType.POLL
        MessageStateBodyType.POLL_CLOSE -> MessageType.POLL

        // Note that in case we don't have the information of what type the deleted message was, we use text as a fallback.
        MessageStateBodyType.DELETED -> MessageType.TEXT
    }
}
