package ch.threema.app.services.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.threema.android.buildBroadcastReceiverIntent
import ch.threema.android.goAsync
import ch.threema.app.di.awaitSessionScopeReady
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotificationDismissedBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val notificationService: NotificationService by inject()

    override fun onReceive(context: Context, intent: Intent) = goAsync {
        awaitSessionScopeReady()
        notificationService.onConversationNotificationDismissed()
    }

    companion object {
        fun createIntent(context: Context) = buildBroadcastReceiverIntent<NotificationDismissedBroadcastReceiver>(context)
    }
}
