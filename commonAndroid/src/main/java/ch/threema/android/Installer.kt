package ch.threema.android

import android.content.Context
import android.os.Build

private const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"
private const val HUAWEI_STORE_PACKAGE_NAME = "com.huawei.appmarket"

fun isInstalledFromStore(context: Context): Boolean =
    when (getInstallerPackageName(context)) {
        PLAY_STORE_PACKAGE_NAME,
        HUAWEI_STORE_PACKAGE_NAME,
        -> true
        else -> false
    }

fun isInstalledFromPlayStore(context: Context): Boolean =
    getInstallerPackageName(context) == PLAY_STORE_PACKAGE_NAME

fun getInstallerPackageName(context: Context): String? =
    try {
        with(context.packageManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                getInstallerPackageName(context.packageName)
            }
        }
    } catch (_: Exception) {
        null
    }
