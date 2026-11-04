package ch.threema.android.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import ch.threema.android.checkGrantedPermission

class PhonePermissionsContract : PermissionContract() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun createIntent(context: Context, input: Unit): Intent =
        createIntent(context, getPermissions())

    companion object {
        fun arePermissionsGranted(context: Context): Boolean =
            getPermissions().all { context.checkGrantedPermission(it) }

        private fun getPermissions() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS)
            } else {
                arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE)
            }
    }
}
