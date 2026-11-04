package ch.threema.app.backuprestore.libthreema

import android.net.Uri

data class BackupCreateParameters(
    /**
     * The uri where the final backup file will be written to.
     */
    val backupUri: Uri,
    /**
     * The password that is used to encrypt the backup.
     */
    val backupPassword: String,
    /**
     * TODO(ANDR-5004): Respect this setting
     */
    val includeMedia: Boolean,
)
