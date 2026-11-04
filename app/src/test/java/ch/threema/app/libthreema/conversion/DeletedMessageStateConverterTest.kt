package ch.threema.app.libthreema.conversion

import ch.threema.libthreema.MessageStateBodyType
import ch.threema.storage.models.MessageType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DeletedMessageStateConverterTest {
    @Test
    fun `conversion of types`() {
        verifyConversion(MessageStateBodyType.TEXT, MessageType.TEXT)
        verifyConversion(MessageStateBodyType.LOCATION, MessageType.LOCATION)
        verifyConversion(MessageStateBodyType.POLL_OPEN, MessageType.POLL)
        verifyConversion(MessageStateBodyType.POLL_CLOSE, MessageType.POLL)
        verifyConversion(MessageStateBodyType.DELETED, MessageType.TEXT)
    }

    private fun verifyConversion(messageStateBodyType: MessageStateBodyType, messageType: MessageType) {
        val messageBodyAndRevisions = DeletedMessageStateConverter().convertToMessageBodyAndRevisions(
            messageStateBodyType = messageStateBodyType,
            deletedAt = TIMESTAMP,
        )
        assertEquals(
            MessageBodyAndRevisions(
                messageType = messageType,
                originalBody = null,
                messageEdits = emptyList(),
                deletedAt = TIMESTAMP,
            ),
            messageBodyAndRevisions,
        )
    }

    companion object {
        private val TIMESTAMP = Instant.ofEpochMilli(1234)
    }
}
