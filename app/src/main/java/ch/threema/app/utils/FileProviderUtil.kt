package ch.threema.app.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object FileProviderUtil {
    /**
     * Get a Uri for the destination file that can be shared to other apps.
     *
     * @param file File to get a Uri for. Must be in a location that is exposed via file_paths.xml.
     * @param filename Desired filename for this file. Can be different from the filename of [file]
     * @return The shareable Uri, using the 'content' scheme
     */
    @JvmStatic
    @JvmOverloads
    fun getUriForFile(context: Context, file: File, filename: String? = null): Uri {
        val authority = context.packageName + ".fileprovider"
        return if (filename != null) {
            FileProvider.getUriForFile(context, authority, file, filename)
        } else {
            FileProvider.getUriForFile(context, authority, file)
        }
    }
}
