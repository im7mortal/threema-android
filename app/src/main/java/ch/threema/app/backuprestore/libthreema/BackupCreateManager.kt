package ch.threema.app.backuprestore.libthreema

import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = getThreemaLogger("BackupCreateManager")

class BackupCreateManager(
    private val backupCreateJobCreator: BackupCreateJob.Creator,
) {
    private val mutex = Mutex()
    private var backupCreateJob: BackupCreateJob? = null

    suspend fun startCreatingBackup(
        backupCreateParameters: BackupCreateParameters,
    ): Deferred<BackupCreateResult> = mutex.withLock {
        logger.debug("startCreatingBackup")

        // In case the currently running backup job runs with the same parameters as requested, we will just continue running the current job.
        backupCreateJob.getBackupFinishedDeferredIfMatchingAndRunning(backupCreateParameters)?.let { backupFinished ->
            return backupFinished
        }

        backupCreateJob?.cancelAndJoin()

        logger.info("Creating new backup create job")
        val newBackupCreateJob = backupCreateJobCreator.startBackupCreateJob(
            backupCreateParameters = backupCreateParameters,
        )
        backupCreateJob = newBackupCreateJob
        return newBackupCreateJob.backupFinished
    }

    private fun BackupCreateJob?.getBackupFinishedDeferredIfMatchingAndRunning(
        backupCreateParameters: BackupCreateParameters,
    ): Deferred<BackupCreateResult>? {
        if (this == null) {
            return null
        }

        return if (!backupFinished.isCompleted && this.backupCreateParameters == backupCreateParameters) {
            backupFinished
        } else {
            null
        }
    }
}
