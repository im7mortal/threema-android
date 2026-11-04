package ch.threema.app.backuprestore.libthreema

sealed interface BackupRestoreResult {
    data object Success : BackupRestoreResult
    data object Failure : BackupRestoreResult
}
