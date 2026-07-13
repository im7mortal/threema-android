package ch.threema.app.usecases

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.R

class CopyToClipboardUseCase(
    private val appContext: Context,
) {
    fun call(text: String) {
        val clipboardManager = appContext.getSystemService<ClipboardManager>()
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))
            appContext.showToast(R.string.generic_copied_to_clipboard_hint)
        } else {
            appContext.showToast(R.string.feature_not_supported, ToastDuration.LONG)
        }
    }
}
