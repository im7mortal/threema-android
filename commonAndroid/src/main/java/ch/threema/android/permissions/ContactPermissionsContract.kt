package ch.threema.android.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import ch.threema.android.checkGrantedPermission

class ContactPermissionsContract : PermissionContract() {
    override fun createIntent(context: Context, input: Unit): Intent =
        createIntent(context, getPermissions())

    companion object {
        @JvmStatic
        fun arePermissionsGranted(context: Context): Boolean =
            getPermissions().all { context.checkGrantedPermission(it) }

        private fun getPermissions() =
            arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.GET_ACCOUNTS,
            )
    }
}
