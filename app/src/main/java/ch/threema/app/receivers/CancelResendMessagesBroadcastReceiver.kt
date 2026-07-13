package ch.threema.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.threema.android.buildBroadcastReceiverIntent
import ch.threema.android.goAsync
import ch.threema.app.di.awaitSessionScopeReady
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ActionEvent
import ch.threema.app.services.MessageService
import ch.threema.app.utils.IntentDataUtil
import ch.threema.storage.models.AbstractMessageModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receive the message models that could not be sent when the notification has been explicitly
 * canceled.
 */
class CancelResendMessagesBroadcastReceiver : BroadcastReceiver(), KoinComponent {
    private val globalEventBuses: GlobalEventBuses by inject()
    private val messageService: MessageService? by injectNullableNonBinding()

    override fun onReceive(context: Context, intent: Intent) = goAsync {
        awaitSessionScopeReady()

        val messageService = messageService ?: return@goAsync
        // It is sufficient to trigger the listener. If the home activity (that manages resending
        // the messages) is not available, this event can be dismissed anyway.
        IntentDataUtil.getAbstractMessageModels(intent, messageService).forEach { messageModel ->
            globalEventBuses.actions.emit(ActionEvent.ResendNotificationDismissed(messageModel))
        }
    }

    companion object {
        fun createIntent(
            context: Context,
            failedMessages: List<AbstractMessageModel>,
        ) = buildBroadcastReceiverIntent<CancelResendMessagesBroadcastReceiver>(context) {
            IntentDataUtil.appendMultipleMessageTypes(failedMessages, this)
        }
    }
}
