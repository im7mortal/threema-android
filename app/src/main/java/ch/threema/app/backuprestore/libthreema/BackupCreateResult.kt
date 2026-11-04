package ch.threema.app.backuprestore.libthreema

import ch.threema.common.ByteSize

sealed interface BackupCreateResult {
    data class Success(
        val fileSize: ByteSize,
        val fileLocation: String?,
        val filename: String,
        val includesMedia: Boolean,
    ) : BackupCreateResult
    data object Failure : BackupCreateResult
}
