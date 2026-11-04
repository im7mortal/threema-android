package ch.threema.data.datatypes

data class MessageHistoryEntry(
    /**
     * Timestamp when the message has been edited and this entry was created.
     */
    val createdAt: ULong,
    /**
     * The body before the message was edited.
     */
    val body: String,
)
