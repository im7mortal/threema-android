package ch.threema.app.compose.common

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import ch.threema.android.hasFlag

/**
 * @param enabled If set to `true`, screenshots and screen recordings are blocked.
 * If set to `false`, the activity's default behavior for blocking screen capture is used.
 */
@Composable
fun BlockScreenCapture(enabled: Boolean = true) {
    val activity = LocalActivity.current
    val alwaysBlock = remember(activity) {
        activity?.window?.hasFlag(WindowManager.LayoutParams.FLAG_SECURE) == true
    }
    if (!alwaysBlock && enabled && activity != null) {
        DisposableEffect(activity) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
