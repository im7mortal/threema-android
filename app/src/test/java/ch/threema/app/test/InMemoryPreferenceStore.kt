package ch.threema.app.test

import ch.threema.app.stores.PreferenceStore
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class InMemoryPreferenceStore(
    initialValues: Map<String, Any?> = emptyMap(),
) : PreferenceStore {
    private val values: MutableMap<String, Any?> = initialValues.toMutableMap()

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun remove(keys: Set<String>) {
        keys.forEach { key ->
            values.remove(key)
        }
    }

    override fun save(key: String, value: String?) {
        saveAny(key, value)
    }

    override fun save(key: String, value: Set<String>) {
        saveAny(key, value)
    }

    override fun save(key: String, value: Map<String, String?>) {
        saveAny(key, value)
    }

    override fun save(key: String, value: Array<String>) {
        saveAny(key, value)
    }

    override fun save(key: String, value: Int) {
        saveAny(key, value)
    }

    override fun save(key: String, value: Boolean) {
        saveAny(key, value)
    }

    override fun save(key: String, value: ByteArray) {
        saveAny(key, value)
    }

    override fun save(key: String, value: Long) {
        saveAny(key, value)
    }

    override fun save(key: String, value: Float) {
        saveAny(key, value)
    }

    override fun save(key: String, value: Instant?) {
        saveAny(key, value)
    }

    override fun getString(key: String): String? =
        values[key] as String?

    override fun getLong(key: String, defaultValue: Long): Long =
        values[key] as Long? ?: defaultValue

    override fun getInt(key: String): Int =
        getInt(key, 0)

    override fun getInt(key: String, defaultValue: Int): Int =
        values[key] as Int? ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float =
        values[key] as Float? ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as Boolean? ?: defaultValue

    override fun watchBoolean(key: String, defaultValue: Boolean): Flow<Boolean> {
        throw NotImplementedError("This is currently not implemented")
    }

    override fun watchLong(key: String, defaultValue: Long): Flow<Long> {
        throw NotImplementedError("This is currently not implemented")
    }

    override fun watchInt(key: String, defaultValue: Int): Flow<Int> {
        throw NotImplementedError("This is currently not implemented")
    }

    override fun watchInstant(key: String): Flow<Instant?> {
        throw NotImplementedError("This is currently not implemented")
    }

    override fun watchString(key: String): Flow<String?> {
        throw NotImplementedError("This is currently not implemented")
    }

    override fun getBytes(key: String): ByteArray =
        values[key] as ByteArray

    override fun getInstant(key: String): Instant? =
        values[key] as Instant?

    @Suppress("UNCHECKED_CAST")
    override fun getStringArray(key: String): Array<String>? =
        values[key] as Array<String>?

    @Suppress("UNCHECKED_CAST")
    override fun getMap(key: String): Map<String, String?> =
        values[key] as Map<String, String>

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String>? =
        values[key] as Set<String>?

    override fun containsKey(key: String): Boolean =
        values.containsKey(key)

    override fun clear() {
        values.clear()
    }

    private fun saveAny(key: String, value: Any?) {
        values[key] = value
    }
}
