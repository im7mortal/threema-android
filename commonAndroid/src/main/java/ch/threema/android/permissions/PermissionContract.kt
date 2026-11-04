package ch.threema.android.permissions

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

abstract class PermissionContract internal constructor() : ActivityResultContract<Unit, Boolean>() {
    private val delegate = ActivityResultContracts.RequestMultiplePermissions()

    final override fun getSynchronousResult(context: Context, input: Unit): SynchronousResult<Boolean>? =
        if (alreadyGranted(context)) {
            SynchronousResult(true)
        } else {
            null
        }

    /**
     * This method should be overridden if [createIntent] may only be called under certain conditions,
     * typically depending on `Build.VERSION.SDK_INT`.
     */
    protected open fun alreadyGranted(context: Context): Boolean = false

    protected fun createIntent(context: Context, permission: String) =
        delegate.createIntent(context, arrayOf(permission))

    protected fun createIntent(context: Context, permissions: Array<String>) =
        delegate.createIntent(context, permissions)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        delegate.parseResult(resultCode, intent).values.all { it }
}
