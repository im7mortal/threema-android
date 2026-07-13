package ch.threema.app.utils

import android.view.View
import android.widget.TextView
import com.google.android.material.R
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar

object SnackbarUtil {

    @JvmStatic
    fun make(view: View, text: String, @Duration duration: Int, maxLines: Int): Snackbar {
        val snackbar = Snackbar.make(view, text, duration)
        snackbar
            .getView()
            .findViewById<TextView?>(R.id.snackbar_text)
            ?.let { contentTextView ->
                contentTextView.maxLines = maxLines
            }
        return snackbar
    }
}
