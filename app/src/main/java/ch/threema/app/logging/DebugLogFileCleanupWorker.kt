package ch.threema.app.logging

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.threema.android.buildPeriodicWorkRequest
import ch.threema.android.setBackoffCriteria
import ch.threema.android.setInitialDelay
import ch.threema.app.BuildConfig
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.workers.WorkerNames
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.lastModifiedTime
import ch.threema.common.minus
import ch.threema.logging.backend.DebugLogFileManager
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("DebugLogFileCleanupWorker")

class DebugLogFileCleanupWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val debugLogFileManager: DebugLogFileManager by inject()
    private val appDirectoryProvider: AppDirectoryProvider by inject()
    private val timeProvider: TimeProvider by inject()

    override suspend fun doWork(): Result {
        deleteOldLogFiles(debugLogFileManager.getLogFiles())
        deleteOldLogFiles(debugLogFileManager.getFallbackLogFiles())
        if (BuildConfig.LOG_TOMBSTONES_FOR_NATIVE_CRASHES) {
            deleteOldTombstones()
        }
        return Result.success()
    }

    private fun deleteOldLogFiles(logFiles: List<File>) {
        val now = timeProvider.get()
        logFiles.forEach { logFile ->
            if (now - logFile.lastModifiedTime() > LOG_FILE_MAX_AGE) {
                logger.info("Deleting log file {}", logFile.name)
                if (!logFile.delete()) {
                    logger.warn("Failed to delete log file")
                }
            }
        }
    }

    private fun deleteOldTombstones() {
        val now = timeProvider.get()
        appDirectoryProvider.tombstonesDirectory.listFiles()?.forEach { tombstoneFile ->
            if (now - tombstoneFile.lastModifiedTime() > TOMBSTONE_FILE_MAX_AGE) {
                logger.info("Deleting tombstone file {}", tombstoneFile.name)
                if (!tombstoneFile.delete()) {
                    logger.warn("Failed to delete tombstone file")
                }
            }
        }
    }

    companion object {
        /**
         * Any debug log file that has not been updated within this time will eventually be deleted by the worker.
         *
         * Since a new debug log file is started every month, and old log files are only deleted at a specific interval, the effective maximum
         * time span covered by debug logs is approximately 1 month + [LOG_FILE_MAX_AGE] + [INTERVAL].
         */
        private val LOG_FILE_MAX_AGE = 10.days

        /**
         * Any tombstone file older than this will eventually be deleted by the worker.
         * We don't want to keep tombstones around for too long because they might contain sensitive data.
         */
        private val TOMBSTONE_FILE_MAX_AGE = 4.days

        /**
         * (Approximate) interval between deletions
         */
        private val INTERVAL = 2.days

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName = WorkerNames.WORKER_DEBUG_LOG_CLEANUP,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                request = buildPeriodicWorkRequest<DebugLogFileCleanupWorker>(INTERVAL) {
                    setInitialDelay(5.minutes)
                    setBackoffCriteria(BackoffPolicy.LINEAR, 1.hours)
                },
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WorkerNames.WORKER_DEBUG_LOG_CLEANUP)
        }
    }
}
