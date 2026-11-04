package ch.threema.app.backuprestore.libthreema

import ch.threema.common.ByteSize

sealed interface BackupCreateState {
    object Initial : BackupCreateState
    data class InProgress(
        val backupStep: BackupStep,
        val includeMedia: Boolean,
    ) : BackupCreateState
    sealed interface Done : BackupCreateState {
        data class Success(
            val fileSize: ByteSize,
            val fileLocation: String?,
            val filename: String,
            val includeMedia: Boolean,
        ) : Done
        data object Failure : Done
    }
}
