package ch.threema.android.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import ch.threema.android.checkGrantedPermission

class NotificationPermissionContract : PermissionContract() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun createIntent(context: Context, input: Unit): Intent =
        createIntent(context, Manifest.permission.POST_NOTIFICATIONS)

    override fun alreadyGranted(context: Context) =
        isPermissionGranted(context)

    companion object {
        fun isPermissionGranted(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkGrantedPermission(Manifest.permission.POST_NOTIFICATIONS)
    }
}
