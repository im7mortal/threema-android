package ch.threema.app.libthreema.conversion

import ch.threema.libthreema.MessageText as LibthreemaMessageText
import ch.threema.libthreema.MessageTextRevision as LibthreemaMessageTextRevision
import ch.threema.libthreema.TextMessageState as LibthreemaTextMessageState
import ch.threema.storage.models.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals

class TextMessageStateConverterTest {
    @Test
    fun `message without revisions`() {
        // Arrange
        val text = "Hello"
        val textMessageState = LibthreemaTextMessageState(
            original = LibthreemaMessageText(text),
            revisions = emptyList(),
        )
        val textMessageStateConverter = TextMessageStateConverter()

        // Act
        val messageBodyAndRevisions = textMessageStateConverter.convertToMessageBodyAndRevisions(textMessageState)

        // Assert
        assertEquals(
            expected = MessageBodyAndRevisions(
                messageType = MessageType.TEXT,
                originalBody = text,
            ),
            actual = messageBodyAndRevisions,
        )
    }

    @Test
    fun `message with revisions`() {
        // Arrange
        val originalText = "Helo"
        val editedText = "Hello"
        val editedTimestamp = 42UL
        val textMessageState = LibthreemaTextMessageState(
            original = LibthreemaMessageText(originalText),
            revisions = listOf(
                LibthreemaMessageTextRevision(
                    createdAt = editedTimestamp,
                    message = LibthreemaMessageText(editedText),
                ),
            ),
        )
        val textMessageStateConverter = TextMessageStateConverter()

        // Act
        val messageBodyAndRevisions = textMessageStateConverter.convertToMessageBodyAndRevisions(textMessageState)

        // Assert
        assertEquals(
            expected = MessageBodyAndRevisions(
                messageType = MessageType.TEXT,
                originalBody = originalText,
                messageEdits = listOf(
                    MessageEdit(
                        createdAt = editedTimestamp,
                        body = editedText,
                    ),
                ),
            ),
            actual = messageBodyAndRevisions,
        )
    }
}
