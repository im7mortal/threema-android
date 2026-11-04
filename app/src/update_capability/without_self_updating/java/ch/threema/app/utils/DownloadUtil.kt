package ch.threema.app.utils

import android.content.Context
import androidx.annotation.AnyThread

object DownloadUtil {
    @AnyThread
    @Suppress("unused")
    fun deleteOldAPKs(context: Context) {
        // nothing to do, this build doesn't support self-updating
    }
}
