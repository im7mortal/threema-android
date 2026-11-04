package ch.threema.app.backuprestore.csv

import androidx.annotation.WorkerThread
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.awaitDisconnected
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * This class serves as an extension to [RestoreService], primarily to allow the use of Kotlin-only features such as coroutines.
 */
class BackupServiceHelper(
    private val lifetimeService: LifetimeService,
) {
    @WorkerThread
    fun waitUntilDisconnected() {
        runBlocking {
            withTimeoutOrNull(20.seconds) {
                lifetimeService.awaitDisconnected()
            }
                ?: error("Failed to disconnect in time")
        }
    }
}
