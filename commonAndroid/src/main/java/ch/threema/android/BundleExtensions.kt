package ch.threema.android

import android.os.Build
import android.os.Bundle

inline fun buildBundle(block: Bundle.() -> Unit): Bundle =
    Bundle().apply(block)

inline fun <reified T> Bundle.getParcelableCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key)
    }
