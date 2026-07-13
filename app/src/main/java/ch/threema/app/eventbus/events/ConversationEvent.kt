package ch.threema.app.eventbus.events

import ch.threema.storage.models.ConversationModel

sealed class ConversationEvent {
    data class NewConversation(val conversation: ConversationModel) : ConversationEvent()

    data class ConversationUpdated(val conversation: ConversationModel) : ConversationEvent()

    data class ConversationRemoved(val conversation: ConversationModel) : ConversationEvent()

    data class ConversationArchived(val conversation: ConversationModel) : ConversationEvent()

    data class ConversationDeleted(val conversation: ConversationModel) : ConversationEvent()

    data object AllConversationsUpdated : ConversationEvent()
}
