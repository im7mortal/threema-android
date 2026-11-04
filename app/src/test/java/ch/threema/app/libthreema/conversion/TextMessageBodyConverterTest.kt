package ch.threema.app.libthreema.conversion

import ch.threema.data.models.EditHistoryEntryData
import ch.threema.libthreema.MessageText as LibthreemaMessageText
import ch.threema.libthreema.MessageTextRevision as LibthreemaMessageTextRevision
import ch.threema.libthreema.TextMessageState as LibthreemaTextMessageState
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TextMessageBodyConverterTest {

    @Test
    fun `text message without edits`() {
        // Arrange
        val messageUid = "muid"
        val text = "This is the text!"
        val messageModels = listOf(
            ContactMessageModel(),
            GroupMessageModel(),
            DistributionListMessageModel(),
        )
        messageModels.forEach { messageModel ->
            messageModel.apply {
                uid = messageUid
                body = text
                createdAt = Instant.ofEpochMilli(123456)
            }
        }
        val textMessageBodyConverter = TextMessageBodyConverter(
            timeProvider = mockk(),
        )
        val expectedTextMessageState = LibthreemaTextMessageState(
            original = LibthreemaMessageText(text),
            revisions = emptyList(),
        )

        // Act
        val textMessageStates = messageModels
            .map { messageModel ->
                textMessageBodyConverter.convertToLibthreemaTextMessageState(
                    messageModel = messageModel,
                    editHistoryEntries = null,
                )
            }

        // Assert
        textMessageStates.forEach { textMessageState ->
            assertEquals(
                expected = expectedTextMessageState,
                actual = textMessageState,
            )
        }
    }

    @Test
    fun `text message with edits`() {
        // Arrange
        val messageUid = "muid1"
        val messageTextHistory = listOf(
            "Original message" to Instant.EPOCH.toEpochMilli(),
            "First edit" to 1L,
            "Second edit" to 2L,
        )
        val messageModels = listOf(
            ContactMessageModel(),
            GroupMessageModel(),
            DistributionListMessageModel(),
        )
        messageModels.forEach { messageModel ->
            messageModel.apply {
                uid = messageUid
                body = messageTextHistory.last().first
                createdAt = Instant.ofEpochMilli(messageTextHistory.first().second)
                editedAt = Instant.ofEpochMilli(messageTextHistory.last().second)
            }
        }
        val editHistoryEntryData = messageTextHistory
            .dropLast(1)
            .reversed()
            .mapIndexed { index, (text, createdAt) ->
                EditHistoryEntryData(
                    uid = index,
                    messageUid = messageUid,
                    text = text,
                    createdAt = Instant.ofEpochMilli(createdAt),
                )
            }

        val textMessageBodyConverter = TextMessageBodyConverter(
            timeProvider = mockk(),
        )
        val expectedRevisions = messageTextHistory
            .drop(1)
            .map { (text, timestamp) ->
                LibthreemaMessageTextRevision(
                    createdAt = timestamp.toULong(),
                    message = LibthreemaMessageText(
                        text = text,
                    ),
                )
            }
        val expectedTextMessageState = LibthreemaTextMessageState(
            original = LibthreemaMessageText(messageTextHistory.first().first),
            revisions = expectedRevisions,
        )

        // Act
        val textMessageStates = messageModels
            .map { messageModel ->
                textMessageBodyConverter.convertToLibthreemaTextMessageState(
                    messageModel = messageModel,
                    editHistoryEntries = editHistoryEntryData,
                )
            }

        // Assert
        textMessageStates.forEach { textMessageState ->
            assertEquals(
                expected = expectedTextMessageState,
                actual = textMessageState,
            )
        }
    }
}
