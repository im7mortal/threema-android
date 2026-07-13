package ch.threema.app.files

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.threema.android.buildOneTimeWorkRequest
import ch.threema.android.buildPeriodicWorkRequest
import ch.threema.android.setInputData
import ch.threema.app.workers.WorkerNames.WORKER_TEMP_FILES_CLEANUP
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.deleteOrThrow
import ch.threema.common.isEmptyDirectory
import ch.threema.common.lastModifiedTime
import ch.threema.common.minus
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("TempFilesCleanupWorker")

class TempFilesCleanupWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val timeProvider: TimeProvider by inject()
    private val appDirectoryProvider: AppDirectoryProvider by inject()

    override suspend fun doWork(): Result {
        val ageThreshold = getAgeThreshold()
        logger.debug("Deleting temp files older than {}", ageThreshold)
        val timeThreshold = timeProvider.get() - ageThreshold
        deleteOldFiles(appDirectoryProvider.cacheDirectory, timeThreshold)
        return Result.success()
    }

    private fun deleteOldFiles(directory: File, threshold: Instant) {
        directory.walkBottomUp()
            .filter { file -> file.lastModifiedTime() < threshold }
            .filter(::canDelete)
            .forEach { file ->
                if (file.isEmptyDirectory) {
                    if (!file.delete()) {
                        logger.warn("Failed to delete temp directory {}", file)
                    }
                } else if (file.isFile) {
                    try {
                        logger.info("Deleting temp file {}", file.path)
                        file.deleteOrThrow()
                    } catch (e: IOException) {
                        logger.error("Failed to delete temp file", e)
                    }
                }
            }
    }

    private fun canDelete(file: File): Boolean {
        if (file.isFile && file.extension == "lck") {
            // Don't delete database lock files, such as androidx.work.workdb.lck
            return false
        }
        if (file.isDirectory && file.name.startsWith("jna-")) {
            // Don't delete the JNA cache directory
            return false
        }
        if (file == appDirectoryProvider.shareDirectory) {
            // Don't delete the "share" directory, as some parts of the app don't expect it to be missing
            return false
        }
        return true
    }

    private fun getAgeThreshold(): Duration =
        inputData.getLong(EXTRA_AGE_THRESHOLD, 0L).milliseconds

    class Scheduler(
        private val workManager: WorkManager,
    ) {
        fun schedulePeriodicCleanup() {
            workManager.enqueueUniquePeriodicWork(
                WORKER_TEMP_FILES_CLEANUP,
                ExistingPeriodicWorkPolicy.UPDATE,
                buildPeriodicWorkRequest<TempFilesCleanupWorker>(REPETITION_INTERVAL) {
                    setInputData {
                        putLong(EXTRA_AGE_THRESHOLD, FILE_AGE_THRESHOLD.inWholeMilliseconds)
                    }
                },
            )
        }

        fun scheduleImmediateFullCleanup() {
            workManager.enqueueUniqueWork(
                WORKER_TEMP_FILES_CLEANUP,
                ExistingWorkPolicy.APPEND,
                buildOneTimeWorkRequest<TempFilesCleanupWorker>(),
            )
        }
    }

    companion object {
        private const val EXTRA_AGE_THRESHOLD = "ageThreshold"

        private val REPETITION_INTERVAL = 10.hours
        private val FILE_AGE_THRESHOLD = 2.hours
    }
}
