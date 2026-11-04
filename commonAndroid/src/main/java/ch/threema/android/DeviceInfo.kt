package ch.threema.android

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.getSystemService

object DeviceInfo {
    @JvmStatic
    fun isXiaomiDevice(): Boolean =
        isManufacturedBy("Xiaomi")

    @JvmStatic
    fun isAmazonDevice(): Boolean =
        isManufacturedBy("Amazon")

    @JvmStatic
    fun isHuaweiDevice(): Boolean =
        isManufacturedBy("Huawei")

    @JvmStatic
    fun isSamsungDevice(): Boolean =
        isManufacturedBy("Samsung")

    @JvmStatic
    fun isSonyDevice(): Boolean =
        isManufacturedBy("Sony")

    @JvmStatic
    fun isNokiaDevice(): Boolean =
        isManufacturedBy("HMD Global")

    private fun isManufacturedBy(manufacturer: String) =
        Build.MANUFACTURER.equals(manufacturer, ignoreCase = true)

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    @JvmStatic
    fun supportsNotificationChannels(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    @JvmStatic
    fun supportsPictureInPicture(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    @JvmStatic
    fun supportsScopedStorage(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Get the supported gl es version on this device, or `null` if the version could not be determined.
     * Note that this check may not work on all devices. On some devices, this returns version 3.0
     * even if only version 2.x is supported.
     */
    @JvmStatic
    fun getSupportedGlEsVersion(context: Context): Double? =
        try {
            context.getSystemService<ActivityManager>()
                ?.deviceConfigurationInfo
                ?.getGlEsVersion()
                ?.toDouble()
        } catch (_: Exception) {
            null
        }
}
