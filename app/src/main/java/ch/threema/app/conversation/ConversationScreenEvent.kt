package ch.threema.app.conversation

sealed interface ConversationScreenEvent {

    data object CheckAppLock : ConversationScreenEvent
}
