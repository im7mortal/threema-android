package ch.threema.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile

fun Uri.isFileUri() =
    scheme?.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) == true

fun Uri.isContentUri() =
    scheme?.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) == true

fun Uri.toTreeUriDocumentFile(context: Context): DocumentFile? =
    when {
        isFileUri() -> DocumentFile.fromFile(toFile())
        else -> try {
            DocumentFile.fromTreeUri(context, this)!!
        } catch (_: IllegalArgumentException) {
            null
        }
    }
