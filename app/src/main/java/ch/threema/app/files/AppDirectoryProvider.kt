package ch.threema.app.files

import android.content.Context
import ch.threema.base.utils.getThreemaLogger
import java.io.File

private val logger = getThreemaLogger("AppDirectoryProvider")

class AppDirectoryProvider(
    private val context: Context,
) {
    /**
     * Directory for storing the user's files, such as message file attachments, images, voice recordings, profile pictures,
     * group profile pictures, wallpapers, ...
     *
     * It is recommended that files in this directory are encrypted, though exceptions are possible.
     */
    val userFilesDirectory: File by lazy {
        createIfNeeded(
            File(context.filesDir, "user-data"),
        )
    }

    /**
     * Directory for storing app specific files, such as app meta and config data, keys, ...
     * For user files, use [userFilesDirectory] instead.
     */
    val appDataDirectory: File = context.filesDir

    /**
     * Directory formerly used for storing the user's encrypted files.
     * Only used for reading old files for backwards compatibility.
     * New files should never be stored here, as the directory may not always be accessible.
     * Use [userFilesDirectory] instead.
     */
    @Deprecated("Use only for reading old files, use userFilesDirectory instead")
    val legacyUserFilesDirectory: File
        get() = File(context.getExternalFilesDir(null), "data")

    /**
     * Directory used for temporary files that may be shared or sent to other apps.
     * All files in this directory will be automatically deleted after a while.
     *
     * Files for which a content-Uri needs to be created (using [ch.threema.app.utils.FileProviderUtil.getUriForFile]) must be placed
     * inside this directory.
     *
     * For temporary files that do not need to be shareable, use [cacheDirectory] instead.
     */
    val shareDirectory: File
        get() = createIfNeeded(
            File(context.cacheDir, "share"),
        )

    /**
     * Directory used for temporary files. All files in this directory will be automatically deleted after a while.
     *
     * For files that need to be shareable, i.e., made accessible to other apps or for which a content-Uri needs to be created,
     * use [shareDirectory] instead.
     */
    val cacheDirectory: File
        get() = context.cacheDir

    private fun createIfNeeded(directory: File): File {
        if (!directory.isDirectory()) {
            if (directory.exists()) {
                directory.delete()
            }
            if (!directory.mkdirs()) {
                logger.warn("Failed to create directory {}", directory)
            }
        }
        return directory
    }
}
