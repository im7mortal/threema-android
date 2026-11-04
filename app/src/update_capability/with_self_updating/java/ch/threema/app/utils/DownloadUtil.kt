package ch.threema.app.utils

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.annotation.AnyThread
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import ch.threema.android.checkGrantedPermission
import ch.threema.app.R
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.deleteOrThrow
import java.io.File

private val logger = getThreemaLogger("DownloadUtil")

/**
 * APK Downloader. Only used in Threema Shop version.
 * The apk file location is managed by the download manager. The deletion
 * of the file should be done automatically by the system, but to be on the safe side
 * we also try to delete it ourselves by calling `deleteOldAPKs` after installation in the
 * UpdateReceiver.
 */
object DownloadUtil {
    /**
     * Starts the download and provides the download state info.
     *
     * @param context the application context
     * @param downloadUrl the download URL
     */
    @JvmStatic
    fun downloadUpdate(context: Context, downloadUrl: String) {
        logger.info("Downloading update")
        val uri = downloadUrl.toUri()
            .buildUpon()
            .appendQueryParameter("download", "true")
            .build()
        download(context, uri)
    }

    /**
     * Starts the download with the download manager.
     *
     * @param context the application context
     * @param url the url of the apk file
     */
    private fun download(context: Context, url: Uri) {
        val destinationFileName = context.getString(R.string.shop_download_filename)
        val request = DownloadManager.Request(url)
        request.setTitle(destinationFileName)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val id = context.getSystemService<DownloadManager>()?.enqueue(request)
        logger.info("Enqueued update download with id {}", id)
    }

    /**
     * Deletes old Threema APKs in the downloads directory. For Android 11 and newer, the files
     * cannot be deleted due to scoped storage. Newer app updates are deleted automatically by the
     * system and therefore this method is no longer needed.
     *
     * @param context needed to get apk file name from string resources
     */
    @AnyThread
    fun deleteOldAPKs(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return
        }

        if (!context.checkGrantedPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return
        }

        RuntimeUtil.runOnWorkerThread {
            val downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val temporaryAPKFile = File(downloadPath.path, context.getString(R.string.shop_download_filename))
            if (temporaryAPKFile.exists()) {
                try {
                    temporaryAPKFile.deleteOrThrow()
                } catch (e: Exception) {
                    logger.error("could not delete old apk file", e)
                }
            }
        }
    }
}
