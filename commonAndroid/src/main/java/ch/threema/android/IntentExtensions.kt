package ch.threema.android

import android.content.Intent
import androidx.core.content.IntentCompat
import java.io.Serializable

inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? =
    IntentCompat.getParcelableExtra(this, key, T::class.java)

inline fun <reified T : Serializable?> Intent.getSerializableExtraCompat(key: String): T? =
    IntentCompat.getSerializableExtra(this, key, T::class.java)

/**
 * Get the int value of the extras or null if no element with [key] is available. Note that 0 is returned in case there is a value for the [key] but
 * it is not an int.
 */
fun Intent.getIntOrNull(key: String): Int? =
    if (extras?.containsKey(key) != true) {
        null
    } else {
        extras?.getInt(key)
    }

/**
 * Get the long value of the extras or null if no element with [key] is available. Note that 0 is returned in case there is a value for the [key] but
 * it is not a long.
 */
fun Intent.getLongOrNull(key: String): Long? =
    if (extras?.containsKey(key) != true) {
        null
    } else {
        extras?.getLong(key)
    }

fun Intent.getStringOrNull(key: String): String? =
    if (extras?.containsKey(key) != true) {
        null
    } else {
        extras?.getString(key)
    }
