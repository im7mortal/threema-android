package ch.threema.app.eventbus.events

import ch.threema.storage.models.MessageModel

sealed class MessageEvent {
    /**
     * Emitted when a new message was created and stored in the database. This includes incoming, outgoing, local-only, and reflected messages.
     */
    data class NewMessage(val message: MessageModel) : MessageEvent()

    /**
     * Emitted when a message was edited by the user.
     */
    data class MessageEdited(val message: MessageModel) : MessageEvent()

    /**
     * Emitted when one or more messages are modified in any way.
     */
    data class MessagesUpdated(val messages: List<MessageModel>) : MessageEvent() {
        constructor(message: MessageModel) : this(listOf(message))
    }

    /**
     * Emitted when a message was removed from the local database.
     * Note that this is NOT emitted when a message was deleted remotely.
     */
    data class MessageRemovedLocally(val message: MessageModel) : MessageEvent()

    /**
     * Emitted when a message is deleted for all by the user.
     */
    data class MessageDeletedForAll(val message: MessageModel) : MessageEvent()
}
