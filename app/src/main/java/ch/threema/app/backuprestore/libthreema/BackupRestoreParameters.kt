package ch.threema.app.backuprestore.libthreema

import java.io.File

data class BackupRestoreParameters(
    val backupFile: File,
    val backupPassword: String,
)
