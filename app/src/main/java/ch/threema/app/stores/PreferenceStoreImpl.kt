package ch.threema.app.stores

import android.content.SharedPreferences
import androidx.core.content.edit
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.decodeFromStringOrNull
import ch.threema.common.hexStringToByteArrayOrNull
import ch.threema.common.takeUnlessEmpty
import ch.threema.common.toHexString
import java.time.Instant
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

private val logger = getThreemaLogger("PreferenceStoreImpl")

class PreferenceStoreImpl(
    private val sharedPreferences: SharedPreferences,
    private val commit: Boolean = false,
) : BasePreferenceStore(), PreferenceStore {

    override fun remove(key: String) {
        sharedPreferences.edit(commit = commit) {
            remove(key)
        }
    }

    override fun remove(keys: Set<String>) {
        sharedPreferences.edit(commit = commit) {
            for (key in keys) {
                remove(key)
            }
        }
    }

    override fun save(key: String, value: String?) {
        sharedPreferences.edit(commit = commit) {
            putString(key, value)
        }
    }

    override fun save(key: String, value: Map<String, String?>) {
        val json = value.encodeToJsonArray()
        save(key, json.toString())
    }

    override fun save(key: String, value: Array<String>) {
        sharedPreferences.edit(commit = commit) {
            putString(key, value.encodeToString())
        }
    }

    override fun save(key: String, value: Long) {
        sharedPreferences.edit(commit = commit) {
            putLong(key, value)
        }
    }

    override fun save(key: String, value: Int) {
        sharedPreferences.edit(commit = commit) {
            putInt(key, value)
        }
    }

    override fun save(key: String, value: Boolean) {
        sharedPreferences.edit(commit = commit) {
            putBoolean(key, value)
        }
    }

    override fun save(key: String, value: ByteArray) {
        sharedPreferences.edit(commit = commit) {
            putString(key, value.toHexString())
        }
    }

    override fun save(key: String, value: Float) {
        sharedPreferences.edit(commit = commit) {
            putFloat(key, value)
        }
    }

    override fun save(key: String, value: Instant?) {
        if (value != null) {
            save(key, value.toEpochMilli())
        } else {
            remove(key)
        }
    }

    override fun getString(key: String): String? =
        try {
            sharedPreferences.getString(key, null)
        } catch (e: ClassCastException) {
            logger.error("Class cast exception", e)
            null
        }

    override fun getStringArray(key: String): Array<String>? =
        sharedPreferences.getString(key, null)
            ?.takeUnlessEmpty()
            ?.decodeToStringArray()

    override fun getMap(key: String): Map<String, String?> =
        try {
            sharedPreferences.getString(key, "[]")
                ?.let { jsonString ->
                    Json.decodeFromStringOrNull<JsonArray>(jsonString)
                }
                ?.decodeToStringMap()
                ?: emptyMap()
        } catch (e: Exception) {
            logger.error("Failed to decode string map", e)
            emptyMap()
        }

    override fun getLong(key: String, defaultValue: Long): Long =
        sharedPreferences.getLong(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Int =
        sharedPreferences.getInt(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        sharedPreferences.getFloat(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        sharedPreferences.getBoolean(key, defaultValue)

    override fun getInstant(key: String): Instant? =
        getLong(key)
            .takeUnless { it == 0L }
            ?.let(Instant::ofEpochMilli)

    override fun getBytes(key: String): ByteArray? =
        sharedPreferences.getString(key, null)
            ?.hexStringToByteArrayOrNull()

    override fun getStringSet(key: String): Set<String>? =
        if (sharedPreferences.contains(key)) {
            sharedPreferences.getStringSet(key, emptySet())
        } else {
            null
        }

    /**
     *  Watch the String value of [key].
     *
     *  See [watchLatest] for details about this flows behavior and the backpressure handling.
     */
    override fun watchString(key: String): Flow<String?> =
        watchLatest(key) {
            getString(key, null)
        }

    /**
     *  Watch the boolean value of [key].
     *
     *  See [watchLatest] for details about this flow's behavior and the backpressure handling.
     */
    override fun watchBoolean(key: String, defaultValue: Boolean): Flow<Boolean> =
        watchLatest(key) {
            getBoolean(key, defaultValue)
        }

    /**
     *  Watch the long value of [key].
     *
     *  See [watchLatest] for details about this flow's behavior and the backpressure handling.
     */
    override fun watchLong(key: String, defaultValue: Long): Flow<Long> =
        watchLatest(key) {
            getLong(key, defaultValue)
        }

    /**
     *  Watch the int value of [key].
     *
     *  See [watchLatest] for details about this flow's behavior and the backpressure handling.
     */
    override fun watchInt(key: String, defaultValue: Int): Flow<Int> =
        watchLatest(key) {
            getInt(key, defaultValue)
        }

    override fun watchInstant(key: String): Flow<Instant?> =
        watchLatest(key) {
            getInstant(key)
        }

    override fun containsKey(key: String): Boolean =
        sharedPreferences.contains(key)

    override fun clear() {
        sharedPreferences.edit(commit = commit) {
            clear()
        }
    }

    /**
     *  Creates a **cold** [Flow] that emits the values produced by [read]. The [read] function will be called right at the start of this flow and on
     *  every change to this [key].
     *
     *  ##### Key removal
     *  If a specific key gets removed via [SharedPreferences.Editor.remove], the [read] function will be called. In case all keys are removed via
     *  [SharedPreferences.Editor.clear], the [read] function will **not** be called. This happens due to the implementation of
     *  [SharedPreferences.OnSharedPreferenceChangeListener.onSharedPreferenceChanged].
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current value.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     */
    private fun <T> watchLatest(
        key: String,
        read: SharedPreferences.() -> T,
    ): Flow<T> =
        callbackFlow {
            // Send the current value (will never suspend because of the defined overflow strategy)
            send(sharedPreferences.read())

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, changedKey: String? ->
                if (changedKey == key) {
                    // Send the updated (or default) value (will never fail because of the defined overflow strategy)
                    trySend(sharedPreferences.read())
                }
            }
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
            .buffer(capacity = CONFLATED)
            .distinctUntilChanged()
}
