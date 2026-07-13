package ch.threema.app.archive

sealed interface ArchiveScreenEvent {
    data object ConversationsUnarchived : ArchiveScreenEvent

    data object ConversationsDeleted : ArchiveScreenEvent

    data class ShowReallyDeleteConversationsDialog(val content: ReallyDeleteConversationsDialogContent) : ArchiveScreenEvent
}
