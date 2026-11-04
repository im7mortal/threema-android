package ch.threema.android

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Checks whether [permission] is granted.
 *
 * Note: This function is intentionally named 'checkGrantedPermission' to satisfy the Android Linter's "MissingPermission" rule,
 * which specifically checks for a method name that starts with "check" and ends with "Permission". As such, we can not freely
 * rename this unless we disable that lint rule.
 */
fun Context.checkGrantedPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Fragment.checkGrantedPermission(permission: String): Boolean =
    requireContext().checkGrantedPermission(permission)

/**
 * @return true if the app with [packageName] exists and declares in its manifest that it may request [permission]
 */
fun checkPermissionInManifest(context: Context, packageName: String, permission: String): Boolean =
    try {
        context.packageManager
            .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            ?.requestedPermissions
            ?.contains(permission)
            ?: false
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
