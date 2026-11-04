package ch.threema.android.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import ch.threema.android.checkGrantedPermission

class CameraPermissionContract : PermissionContract() {
    override fun createIntent(context: Context, input: Unit): Intent =
        createIntent(context, Manifest.permission.CAMERA)

    companion object {
        @JvmStatic
        fun isPermissionGranted(context: Context): Boolean =
            context.checkGrantedPermission(Manifest.permission.CAMERA)
    }
}
