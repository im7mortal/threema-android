package ch.threema.android.permissions

import android.Manifest
import android.content.Context
import ch.threema.android.checkGrantedPermission

class ReadPhoneStatePermissionContract : PermissionContract() {
    override fun createIntent(context: Context, input: Unit) =
        createIntent(context, Manifest.permission.READ_PHONE_STATE)

    companion object {
        fun isPermissionGranted(context: Context): Boolean =
            context.checkGrantedPermission(Manifest.permission.READ_PHONE_STATE)
    }
}
