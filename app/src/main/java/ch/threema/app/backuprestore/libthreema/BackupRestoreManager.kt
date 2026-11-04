package ch.threema.app.backuprestore.libthreema

import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = getThreemaLogger("BackupRestoreManager")

class BackupRestoreManager(
    private val backupRestoreJobCreator: BackupRestoreJob.Creator,
) {
    private val mutex = Mutex()

    suspend fun restoreBackup(
        backupRestoreParameters: BackupRestoreParameters,
    ): BackupRestoreResult = mutex.withLock {
        logger.info("Creating new backup restore job")
        return backupRestoreJobCreator.startBackupRestoreJob(
            restoreParameters = backupRestoreParameters,
        ).backupRestorationFinished.await()
    }
}
