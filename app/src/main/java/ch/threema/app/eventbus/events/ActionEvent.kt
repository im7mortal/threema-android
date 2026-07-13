package ch.threema.app.eventbus.events

import ch.threema.data.datatypes.ConversationId
import ch.threema.storage.models.AbstractMessageModel

/**
 * An [ActionEvent] is an event triggered in the UI, typically related to a user action.
 */
sealed class ActionEvent {
    data class ConversationOpened(val conversationId: ConversationId) : ActionEvent()

    data class QrCodeScanned(val scannedText: String) : ActionEvent()

    data class ResendNotificationDismissed(val message: AbstractMessageModel) : ActionEvent()

    data class VoiceMessagePlaybackEnded(val message: AbstractMessageModel) : ActionEvent()
}
