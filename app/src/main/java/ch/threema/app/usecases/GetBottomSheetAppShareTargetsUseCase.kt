package ch.threema.app.usecases

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.WorkerThread
import ch.threema.app.ui.BottomSheetItem
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.MimeUtil

/**
 * Collects a list of suitable apps that can be used for sharing to, using a bottom sheet.
 */
class GetBottomSheetAppShareTargetsUseCase(
    private val appContext: Context,
) {
    @WorkerThread
    @JvmOverloads
    fun call(mimeType: String = MimeUtil.MIME_TYPE_TEXT): List<BottomSheetItem> {
        val packageManager = appContext.packageManager
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
        val messageApps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filterNotNull()
            .mapNotNull { resolveInfo ->
                val label = resolveInfo.loadLabel(packageManager)
                val icon = resolveInfo.loadIcon(packageManager)
                    ?: return@mapNotNull null
                val bitmap = BitmapUtil.getBitmapFromVectorDrawable(icon, null)
                    ?: return@mapNotNull null

                BottomSheetItem(bitmap, label.toString(), resolveInfo.activityInfo.packageName)
            }
        return messageApps
    }
}
