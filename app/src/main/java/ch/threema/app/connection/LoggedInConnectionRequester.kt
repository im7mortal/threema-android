package ch.threema.app.connection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.content.getSystemService
import ch.threema.android.getPendingIntentForBroadcast
import ch.threema.app.GlobalAppState
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.services.LifetimeService
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.domain.protocol.connection.ConnectionState
import kotlin.time.Duration.Companion.milliseconds
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("LoggedInConnectionRequester")

class LoggedInConnectionRequester(
    private val appContext: Context,
    private val globalAppState: GlobalAppState,
    private val timeProvider: TimeProvider,
) : KoinComponent {
    private val serverConnectionManager: ServerConnectionManager? by injectNullableNonBinding()
    private val lifetimeService: LifetimeService? by injectNullableNonBinding()
    private val alarmManager: AlarmManager?
        get() = appContext.getSystemService()

    private var requireLoggedInConnectionIntent: PendingIntent? = null

    fun requireLoggedInConnection(nextCheckMs: Int) {
        logger.debug("requireLoggedInConnection")
        val serverConnectionManager = serverConnectionManager ?: return
        val lifetimeService = lifetimeService ?: return
        val now = timeProvider.get()

        if (serverConnectionManager.connection.connectionState == ConnectionState.LOGGED_IN) {
            return
        }

        lifetimeService.runAfterNextConnectionStop {
            val lastLoggedIn = globalAppState.lastLoggedIn
            if (lastLoggedIn == null || lastLoggedIn.isBefore(now)) {
                val nextCheck = nextCheckMs.milliseconds

                logger.info("could not log in to Threema server, trying again in {}", nextCheck)

                cancelLoggedInConnection()

                val intent = LifetimeServiceBroadcastReceiver.createRequireConnectionIntent(appContext, nextCheck)
                val requireLoggedInConnectionIntent = getPendingIntentForBroadcast(
                    context = appContext,
                    requestCode = 0,
                    intent = intent,
                    flags = IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE,
                )
                this.requireLoggedInConnectionIntent = requireLoggedInConnectionIntent
                alarmManager?.set(
                    AlarmManager.RTC_WAKEUP,
                    now.toEpochMilli() + nextCheckMs,
                    requireLoggedInConnectionIntent,
                )
            }
        }
    }

    fun cancelLoggedInConnection() {
        val intent = requireLoggedInConnectionIntent ?: return
        val alarmManager = alarmManager ?: return
        logger.debug("cancel cancelLoggedInConnection")
        alarmManager.cancel(intent)
        requireLoggedInConnectionIntent = null
    }
}
