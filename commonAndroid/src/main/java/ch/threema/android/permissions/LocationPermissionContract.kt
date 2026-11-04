package ch.threema.android.permissions

import android.Manifest
import android.content.Context
import android.content.Intent

class LocationPermissionContract : PermissionContract() {
    override fun createIntent(context: Context, input: Unit): Intent =
        createIntent(context, getPermissions())

    companion object {
        private fun getPermissions() =
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
    }
}
