package ch.threema.android.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import ch.threema.android.checkGrantedPermission

class RecordAudioPermissionContract : PermissionContract() {
    override fun createIntent(context: Context, input: Unit): Intent =
        createIntent(context, Manifest.permission.RECORD_AUDIO)

    companion object {
        fun isPermissionGranted(context: Context): Boolean =
            context.checkGrantedPermission(Manifest.permission.RECORD_AUDIO)
    }
}
