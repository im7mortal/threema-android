package ch.threema.app.fragments.composemessage

import ch.threema.storage.models.MessageModel

sealed interface ComposeMessageEvent {

    data class NextRecordsLoaded(
        @JvmField val messageModels: List<MessageModel>,
        @JvmField val hasMoreRecords: Boolean,
    ) : ComposeMessageEvent

    data object ReportingGroupFailed : ComposeMessageEvent
}
