package ch.threema.app.monitors

import androidx.annotation.VisibleForTesting
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.logging.logAndReportError
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex

private val logger = getThreemaLogger("MonitorController")

class MonitorController(
    private val monitorProvider: MonitorProvider,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val mutex = Mutex()

    suspend fun run() = coroutineScope {
        if (!mutex.tryLock()) {
            error("Monitor is already running")
        }
        try {
            supervisorScope {
                monitorProvider.monitors.forEach { monitor ->
                    launch(dispatcherProvider.worker) {
                        logger.debug("Starting {}", monitor.name)
                        while (isActive) {
                            try {
                                monitor.run()
                                logger.error("Monitor {} unexpectedly stopped", monitor.name)
                            } catch (e: Throwable) {
                                logger.logAndReportError("Monitor {} failed", monitor.name, e)
                            }

                            // Monitors need to always run, so we restart them when they unexpectedly stop.
                            // We delay the restarting by a few seconds to reduce the impact of potential crash-loops
                            delay(MONITOR_RESTART_DELAY)
                            logger.info("Restarting {}", monitor.name)
                        }
                    }
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        @VisibleForTesting
        val MONITOR_RESTART_DELAY = 10.seconds
    }
}
