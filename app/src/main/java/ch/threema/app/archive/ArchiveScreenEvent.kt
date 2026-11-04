package ch.threema.app.archive

sealed interface ArchiveScreenEvent {
    data object ConversationsUnarchived : ArchiveScreenEvent

    data class ConversationsDeleted(val count: Int) : ArchiveScreenEvent

    data class ShowReallyDeleteConversationsDialog(val content: ReallyDeleteConversationsDialogContent) : ArchiveScreenEvent
}
