package ch.threema.app.connection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import androidx.core.content.getSystemService
import ch.threema.android.acquire
import ch.threema.android.buildBroadcastReceiverIntent
import ch.threema.android.releaseIfHeld
import ch.threema.app.BuildConfig
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.di.injectNonBinding
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.PollingHelper
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("LifetimeServiceBroadcastReceiver")

class LifetimeServiceBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val loggedInConnectionRequester: LoggedInConnectionRequester by inject()
    private val dispatcherProvider: DispatcherProvider by inject()
    private val lifetimeService: LifetimeService by injectNonBinding()

    private val coroutineScope by lazy {
        CoroutineScope(dispatcherProvider.worker)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        coroutineScope.launch {
            awaitAppFullyReadyWithTimeout(timeout = 20.seconds)
                ?: return@launch
            handleIntent(appContext, intent)
        }
    }

    private fun handleIntent(appContext: Context, intent: Intent) {
        try {
            val wakeLock: WakeLock? = createWakeLock(appContext)
            wakeLock?.acquire(2.minutes)
            if (intent.getBooleanExtra(EXTRA_REQUIRE_LOGGED_IN_CONNECTION, false)) {
                logger.info("Alarm for required logged in connection received")

                val pollingHelper = PollingHelper(appContext, "requireLoggedInConnection")
                loggedInConnectionRequester.cancelLoggedInConnection()
                if (pollingHelper.poll(true)) {
                    val nextCheckMs = intent.getIntExtra(EXTRA_NEXT_CHECK, 1.minutes.inWholeMilliseconds.toInt()) * 4
                    loggedInConnectionRequester.requireLoggedInConnection(nextCheckMs)
                }
            } else {
                val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
                logger.info("Alarm type {} received", requestCode)

                logger.info("Alarm type {} dispatch to LifetimeService START", requestCode)
                lifetimeService.handleAlarm(requestCode)
            }
            wakeLock?.releaseIfHeld()
        } catch (e: Exception) {
            logger.error("Exception", e)
        }
    }

    private fun createWakeLock(context: Context): WakeLock? =
        try {
            return context.getSystemService<PowerManager>()?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                BuildConfig.APPLICATION_ID + ":LifetimeServiceBroadcastReceiver",
            )
        } catch (_: Exception) {
            null
        }

    companion object {

        @JvmStatic
        fun createRequestCodeIntent(context: Context, requestCode: Int) = buildBroadcastReceiverIntent<LifetimeServiceBroadcastReceiver>(context) {
            putExtra(EXTRA_REQUEST_CODE, requestCode)
        }

        @JvmStatic
        fun createRequireConnectionIntent(
            context: Context,
            nextCheck: Duration,
        ) = buildBroadcastReceiverIntent<LifetimeServiceBroadcastReceiver>(context) {
            putExtra(EXTRA_REQUIRE_LOGGED_IN_CONNECTION, true)
            putExtra(EXTRA_NEXT_CHECK, nextCheck.inWholeMilliseconds)
        }

        private const val EXTRA_REQUEST_CODE = "requestCode"
        private const val EXTRA_REQUIRE_LOGGED_IN_CONNECTION = "requireLoggedInConnection"
        private const val EXTRA_NEXT_CHECK = "nextCheck"
    }
}
