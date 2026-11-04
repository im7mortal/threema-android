package ch.threema.app.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.core.content.getSystemService
import ch.threema.android.getPendingIntentForBroadcast
import ch.threema.app.GlobalAppState
import ch.threema.app.connection.LifetimeServiceBroadcastReceiver
import ch.threema.app.connection.ServerConnectionManager
import ch.threema.app.connection.ServerConnectionManager.NoIdentityException
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.common.DispatcherProvider
import ch.threema.domain.taskmanager.TaskManager
import kotlin.concurrent.Volatile
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("LifetimeServiceImpl")

/**
 * @see LifetimeService
 */
@AnyThread
class LifetimeServiceImpl(
    private val appContext: Context,
    private val serverConnectionManager: ServerConnectionManager,
    private val downloadService: DownloadService,
    private val taskManager: TaskManager,
    private val globalAppState: GlobalAppState,
    private val timeSource: TimeSource.WithComparableMarks,
    dispatcherProvider: DispatcherProvider,
) : LifetimeService {

    private val coroutineScope = CoroutineScope(dispatcherProvider.worker)

    private val isActiveFlow = MutableStateFlow(false)

    init {
        coroutineScope.launch {
            globalAppState.watchSpecialMode()
                .map { it != null }
                .dropWhile { !it }
                .collect { isInSpecialMode ->
                    if (isInSpecialMode) {
                        cleanupConnection()
                    } else {
                        startConnectionIfNeeded()
                    }
                }
        }
    }

    @Volatile
    private var paused = false

    private val connectionSlots = mutableMapOf<LifetimeService.SourceTag, ConnectionSlot>()

    /**
     * time until which the connection must stay active in any case
     */
    private var lingerUntil: ComparableTimeMark? = null

    private val afterNextConnectionStopRunnables = mutableSetOf<Runnable>()

    /**
     * A reserved "connection slot".
     */
    private data class ConnectionSlot(val unpauseable: Boolean)

    @Synchronized
    override fun acquireConnection(sourceTag: LifetimeService.SourceTag, unpauseable: Boolean) {
        if (connectionSlots.containsKey(sourceTag)) {
            logger.warn("acquireConnection: tag {} already present in connectionSlots", sourceTag)
            return
        }

        connectionSlots[sourceTag] = ConnectionSlot(unpauseable)
        logger.info("acquireConnection: tag={}, new #slots={}", sourceTag, connectionSlots.size)

        startConnectionIfNeeded()
    }

    @Synchronized
    override fun releaseConnection(sourceTag: LifetimeService.SourceTag) {
        if (connectionSlots.isEmpty()) {
            logger.warn("releaseConnection: #slots is already 0! (source = {})", sourceTag)
            return
        }

        val previousSlot = connectionSlots.remove(sourceTag)
        if (previousSlot == null) {
            logger.warn("releaseConnection: tag {} was not present in connectionSlots", sourceTag)
        }

        logger.info("releaseConnection: tag={}, #slots={}", sourceTag, connectionSlots.size)

        cleanupConnection()
    }

    @Synchronized
    override fun releaseConnectionLinger(sourceTag: LifetimeService.SourceTag, timeoutMs: Long) {
        val timeout = timeoutMs.milliseconds
        if (connectionSlots.isEmpty()) {
            logger.warn("releaseConnectionLinger: #slots is already 0! (source={})", sourceTag)
            return
        }

        val previousSlot = connectionSlots.remove(sourceTag)
        if (previousSlot == null) {
            logger.warn("releaseConnectionLinger: tag {} was not present in connectionSlots", sourceTag)
        }

        logger.info("releaseConnectionLinger: tag={}, #slots={}, linger={}", sourceTag, connectionSlots.size, timeout)

        val newLingerUntil = timeSource.markNow() + timeout
        if (lingerUntil == null || newLingerUntil > lingerUntil!!) {
            lingerUntil = newLingerUntil
            logger.info("Rescheduling release in {}", timeout)
            cancelAlarm(REQUEST_CODE_RELEASE)
            scheduleAlarm(REQUEST_CODE_RELEASE, timeout)
        }
    }

    @Synchronized
    override fun startConnectionIfNeeded() {
        if (connectionSlots.isEmpty()) {
            logger.debug("refresh: No connection slots active")
            return
        }
        if (isActive) {
            logger.debug("refresh: A connection is already active")
            return
        }

        // Do not start a connection if the app is in a special mode
        globalAppState.specialMode?.let { specialMode ->
            logger.info("refresh: Skipping, in special mode {}", specialMode)
            return
        }

        try {
            serverConnectionManager.startConnection()
        } catch (_: NoIdentityException) {
            logger.info("refresh: not starting, no identity found")
            return
        } catch (e: Exception) {
            logger.error("refresh: startConnection failed", e)
            return
        }
        logger.info("refresh: Connection started")
        isActiveFlow.value = true
    }

    @Synchronized
    override fun handleAlarm(requestCode: Int) {
        logger.info("Alarm type {} (handling) START", requestCode)
        when (requestCode) {
            REQUEST_CODE_RELEASE -> withTimeMeasurement(logger, "handleAlarm.release") {
                cleanupConnection()
            }
            REQUEST_CODE_RESEND -> withTimeMeasurement(logger, "handleAlarm.resend") {
                withConnection(sourceTag = "resendAlarm", linger = MESSAGE_RESEND_INTERVAL)
            }
        }
    }

    /**
     * Requests that the connection gets paused.
     * Note that whether the connection can actually be paused depends on various factors, see [cleanupConnection] for details.
     * This method and [unpause] should only be called in response to [android.os.PowerManager.isDeviceIdleMode] changing its value.
     * This mechanism for pausing the connection was introduced to circumvent a bug where a broadcast never unpauses the connection.
     * See ANDR-2337 for details.
     */
    @Synchronized
    override fun pause() {
        logger.info("Pausing connection")
        paused = true
        cleanupConnection()
    }

    @Synchronized
    override fun unpause() {
        if (paused) {
            logger.info("Unpausing connection")
            paused = false
        } else {
            logger.info("Cannot unpause, connection is not paused")
        }
        startConnectionIfNeeded()
    }

    override fun runAfterNextConnectionStop(runnable: Runnable) {
        synchronized(afterNextConnectionStopRunnables) {
            afterNextConnectionStopRunnables.add(runnable)
        }
    }

    /**
     * Disconnects, if allowed.
     */
    @Synchronized
    private fun cleanupConnection() {
        if (!isActive) {
            logger.debug("cleanupConnection: Nothing to clean up, connection not active")
            return
        }

        globalAppState.specialMode?.let { specialMode ->
            // If we are in a special mode, we want to always disconnect, regardless of other conditions
            logger.info("cleanupConnection: Disconnecting for special mode {}", specialMode)
            disconnect()
            return
        }

        val unpausableSlots = connectionSlots.filter { (_, slot) -> slot.unpauseable }
        if (unpausableSlots.isNotEmpty()) {
            logger.info(
                "cleanupConnection: Not cleaning up, as there are unpausable slots, #slots={}, unpausable-slots=[{}]",
                connectionSlots.size,
                unpausableSlots.keys.joinToString(),
            )
            return
        }

        lingerUntil?.let { lingerUntil ->
            val currentTime = timeSource.markNow()
            if (lingerUntil > currentTime && !paused) {
                logger.info("cleanupConnection: Connection must linger for another {}", lingerUntil - currentTime)
                return
            }
        }

        if (downloadService.isDownloading()) {
            logger.info("cleanupConnection: Still downloading - linger on")
            cancelAlarm(REQUEST_CODE_RELEASE)
            withConnection(sourceTag = "ongoingDownload", linger = MESSAGE_SEND_TIME)
            return
        }

        if (connectionSlots.isNotEmpty() && !paused) {
            logger.info("cleanupConnection: Not cleaning up, slots=[{}]", connectionSlots.keys.joinToString())
            return
        }

        disconnect()
    }

    @Synchronized
    private fun disconnect() {
        var interrupted = false
        try {
            serverConnectionManager.stopConnection()
        } catch (_: InterruptedException) {
            logger.error("Interrupted while stopping connection")
            interrupted = true
            // Connection cleanup is important, so postpone the interruption here.
        }

        synchronized(afterNextConnectionStopRunnables) {
            for (runnable in afterNextConnectionStopRunnables) {
                runnable.run()
            }
            afterNextConnectionStopRunnables.clear()
        }

        isActiveFlow.value = false
        logger.info("Connection closed, #slots={}", connectionSlots.size)

        if (taskManager.hasPendingTasks()) {
            logger.info("Tasks are pending; scheduling resend in {}", MESSAGE_RESEND_INTERVAL)
            scheduleAlarm(REQUEST_CODE_RESEND, MESSAGE_RESEND_INTERVAL)
        }

        if (interrupted) {
            // Re-set interrupted flag
            Thread.currentThread().interrupt()
        }
    }

    private fun scheduleAlarm(requestCode: Int, delay: Duration) {
        try {
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay.inWholeMilliseconds,
                createPendingIntentForRequestCode(requestCode),
            )
        } catch (e: Exception) {
            logger.error("Failed to schedule alarm", e)
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        alarmManager?.cancel(createPendingIntentForRequestCode(requestCode))
    }

    private fun createPendingIntentForRequestCode(requestCode: Int): PendingIntent {
        val intent = LifetimeServiceBroadcastReceiver.createRequestCodeIntent(appContext, requestCode)
        return getPendingIntentForBroadcast(
            context = appContext,
            requestCode = requestCode,
            intent = intent,
            flags = IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE,
        )
    }

    private val alarmManager: AlarmManager?
        get() = appContext.getSystemService()

    override val isActive: Boolean
        get() = isActiveFlow.value

    override fun watchIsActive(): StateFlow<Boolean> =
        isActiveFlow

    override fun destroy() {
        coroutineScope.cancel()
    }

    companion object {
        private val MESSAGE_SEND_TIME = 30.seconds
        private val MESSAGE_RESEND_INTERVAL = 5.minutes

        private const val REQUEST_CODE_RELEASE = 1
        private const val REQUEST_CODE_RESEND = 2
    }
}
