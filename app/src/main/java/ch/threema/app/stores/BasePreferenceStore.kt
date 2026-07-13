package ch.threema.app.stores

import ch.threema.common.primitiveOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull

abstract class BasePreferenceStore {
    protected fun Map<String, String?>.encodeToJsonArray(): JsonArray {
        val map = this
        return buildJsonArray {
            for ((key, value) in map) {
                addJsonArray {
                    add(key)
                    add(value)
                }
            }
        }
    }

    protected fun JsonArray.decodeToStringMap(): Map<String, String?> {
        val jsonArray = this
        return buildMap {
            jsonArray.forEach { item ->
                (item as? JsonArray)
                    ?.let { innerArray ->
                        val key = innerArray.getOrNull(0)?.primitiveOrNull?.contentOrNull
                        val value = innerArray.getOrNull(1)?.primitiveOrNull?.contentOrNull
                        if (key != null) {
                            put(key, value)
                        }
                    }
            }
        }
    }

    protected fun Array<String>.encodeToString(): String {
        require(none { STRING_ARRAY_SEPARATOR in it && it.isNotEmpty() })
        return joinToString(separator = STRING_ARRAY_SEPARATOR)
    }

    protected fun String.decodeToStringArray(): Array<String> =
        split(STRING_ARRAY_SEPARATOR).dropLastWhile(String::isEmpty).toTypedArray()

    companion object {
        private const val STRING_ARRAY_SEPARATOR = ";"
    }
}
