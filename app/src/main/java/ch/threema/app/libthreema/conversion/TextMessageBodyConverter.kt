package ch.threema.app.libthreema.conversion

import ch.threema.common.TimeProvider
import ch.threema.data.models.EditHistoryEntryData
import ch.threema.libthreema.MessageText as LibthreemaMessageText
import ch.threema.libthreema.MessageTextRevision as LibthreemaMessageTextRevision
import ch.threema.libthreema.TextMessageState as LibthreemaTextMessageState
import ch.threema.storage.models.MessageModel
import java.time.Instant

class TextMessageBodyConverter(
    private val timeProvider: TimeProvider,
) {
    private data class MessageHistoryEntry(
        val text: String,
        val createdAt: Instant,
    ) {
        fun toLibthreemaMessageTextRevision() = LibthreemaMessageTextRevision(
            createdAt = createdAt.toEpochMilli().toULong(),
            message = LibthreemaMessageText(text),
        )

        companion object {
            fun fromEditHistoryEntryData(editHistoryEntryData: EditHistoryEntryData) = MessageHistoryEntry(
                text = editHistoryEntryData.text.orEmpty(),
                createdAt = editHistoryEntryData.createdAt,
            )
        }
    }

    fun convertToLibthreemaTextMessageState(
        messageModel: MessageModel,
        editHistoryEntries: List<EditHistoryEntryData>?,
    ): LibthreemaTextMessageState {
        val messageHistoryIncludingOriginal = getMessageHistoryIncludingOriginal(messageModel, editHistoryEntries)

        // The first element of the message history contains the original message.
        val originalMessage = LibthreemaMessageText(
            text = messageHistoryIncludingOriginal.first().text,
        )

        // The other entries contain edits.
        val revisions = messageHistoryIncludingOriginal
            .drop(1)
            .map(MessageHistoryEntry::toLibthreemaMessageTextRevision)

        return LibthreemaTextMessageState(
            original = originalMessage,
            revisions = revisions,
        )
    }

    private fun getMessageHistoryIncludingOriginal(
        messageModel: MessageModel,
        editHistoryEntries: List<EditHistoryEntryData>?,
    ): List<MessageHistoryEntry> {
        // The message history contains every message edit with the timestamp when it was created in the order it was saved to the database. Note that
        // this includes the original message at the beginning and the latest edit at the end.
        val messageHistory = editHistoryEntries
            ?.reversed()
            ?.map(MessageHistoryEntry::fromEditHistoryEntryData)

        val latestMessageHistoryEntry = MessageHistoryEntry(
            text = messageModel.body.orEmpty(),
            createdAt = messageModel.editedAt
                ?: messageModel.createdAt
                ?: timeProvider.get(),
        )
        return if (messageHistory == null) {
            listOf(latestMessageHistoryEntry)
        } else {
            messageHistory + latestMessageHistoryEntry
        }
    }
}
