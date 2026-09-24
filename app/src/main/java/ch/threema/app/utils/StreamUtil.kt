package ch.threema.app.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import ch.threema.base.utils.getThreemaLogger
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

private val logger = getThreemaLogger("StreamUtil")

@Throws(IOException::class)
fun getFromUri(context: Context, uri: Uri?): InputStream {
    if (uri == null || uri.scheme == null) {
        throw FileNotFoundException()
    }

    if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
        try {
            context.contentResolver.openInputStream(uri)
                ?.let { inputStream ->
                    return inputStream
                }
        } catch (_: FileNotFoundException) {
            logger.info("Unable to get an InputStream for this file using ContentResolver: $uri")
        } catch (e: SecurityException) {
            throw IOException(e)
        }
    }

    // try to open as local file if openInputStream fails for a content Uri
    val filePath = FileUtil.getRealPathFromURI(context, uri)
        ?: throw FileNotFoundException()
    val tmpPath = context.cacheDir.absolutePath
    val appPath = context.applicationInfo.dataDir

    // do not allow sending of files from local directories - but allow tmp dir
    if (filePath.startsWith(appPath) && !filePath.startsWith(tmpPath)) {
        throw FileNotFoundException("File on private directory")
    }
    return FileInputStream(filePath)
}
