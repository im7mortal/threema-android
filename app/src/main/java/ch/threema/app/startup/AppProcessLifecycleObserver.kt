package ch.threema.app.startup

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ch.threema.app.di.awaitSessionScopeReady
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.releaseConnectionLinger
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("AppProcessLifecycleObserver")

class AppProcessLifecycleObserver(
    private val reloadAppRestrictions: () -> Unit,
    dispatcherProvider: DispatcherProvider,
) : DefaultLifecycleObserver, KoinComponent {

    private val lifetimeService: LifetimeService? by injectNullableNonBinding()

    /**
     * Note that this object follows a last one wins approach regarding connection acquisition and release. Intermediate connection acquisitions and
     * releases may be skipped.
     */
    private val connectionHolder = object {
        private val coroutineScope = CoroutineScope(dispatcherProvider.worker)
        private var lifetimeServiceAcquisitionJob: Job? = null
        private var lifetimeServiceReleaseJob: Job? = null

        /**
         * Acquire a permanent connection.
         */
        fun acquire() {
            lifetimeServiceAcquisitionJob = coroutineScope.launch {
                // Wait until release job is complete (or canceled) to ensure it is acquired afterward
                lifetimeServiceReleaseJob?.cancelAndAwaitForCancellation()

                awaitSessionScopeReady()
                lifetimeService?.let { lifetimeService ->
                    lifetimeService.acquireConnection(APP_PROCESS_SOURCE_TAG)
                    logger.info("Connection now acquired")
                }
            }
        }

        /**
         * Release the permanent connection.
         */
        fun release() {
            lifetimeServiceReleaseJob = coroutineScope.launch {
                // Wait until acquisition is complete (or canceled) to ensure it is released afterward
                lifetimeServiceAcquisitionJob?.cancelAndAwaitForCancellation()

                val lifetimeService = lifetimeService
                if (lifetimeService != null) {
                    lifetimeService.releaseConnectionLinger(
                        sourceTag = APP_PROCESS_SOURCE_TAG,
                        timeout = 1.minutes,
                    )
                    logger.info("Connection linger released")
                } else {
                    logger.warn("Could not release connection linger")
                }
            }
        }

        private suspend fun Job.cancelAndAwaitForCancellation() {
            try {
                cancelAndJoin()
            } catch (_: CancellationException) {
                // Nothing to do
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now created")
    }

    override fun onStart(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now visible")
    }

    override fun onResume(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now resumed")
        connectionHolder.acquire()
        reloadAppRestrictions()
    }

    override fun onPause(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now paused")
        connectionHolder.release()
    }

    override fun onStop(owner: LifecycleOwner) {
        logger.info("*** Lifecycle: App now stopped")
    }

    companion object {
        @VisibleForTesting
        const val APP_PROCESS_SOURCE_TAG = "AppProcess"
    }
}
