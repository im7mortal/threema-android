package ch.threema.common

import kotlin.collections.component1
import kotlin.collections.component2
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

fun JSONArray.toIntArray() = IntArray(length(), ::getInt)

fun JSONArray.toJSONObjectList(): List<JSONObject> = List(length(), ::getJSONObject)

fun JSONObject.getStringOrNull(name: String): String? =
    if (has(name)) optString(name) else null

/**
 * Decode the json array with the given [name] of the json object to an instance of T.
 *
 * @throws org.json.JSONException if there is no array with the given [name]
 * @throws IllegalArgumentException if the json array is no valid instance of T
 * @throws kotlinx.serialization.SerializationException in case of any decoding-specific error
 */
inline fun <reified T> JSONObject.decodeArray(name: String): T =
    Json.decodeFromString(getJSONArray(name).toString())

/**
 * Converts the provided [map] to a JSON object and inserts it into the JSON array.
 * Primitive values and nulls in the map will be taken as-is, everything else is converted to a string.
 */
@OptIn(ExperimentalSerializationApi::class)
fun JsonArrayBuilder.addObject(map: Map<String, Any?>) {
    addJsonObject {
        map.forEach { (key, metaValue) ->
            when (metaValue) {
                is Number -> put(key, metaValue)
                is Boolean -> put(key, metaValue)
                null -> put(key, null)
                else -> put(key, metaValue.toString())
            }
        }
    }
}

inline fun <reified T : Any> Json.decodeFromStringOrNull(string: String): T? =
    try {
        decodeFromString(string)
    } catch (_: IllegalArgumentException) {
        null
    }

val JsonElement.primitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

val JsonElement.intOrNull: Int?
    get() = primitiveOrNull?.intOrNull

val JsonElement.booleanOrNull: Boolean?
    get() = primitiveOrNull?.booleanOrNull

@Deprecated("Use more structured data objects instead of raw maps whenever possible")
fun parseJsonObjectAsStringMap(jsonObject: String): Map<String, String?> =
    Json.decodeFromString<Map<String, JsonElement?>>(jsonObject)
        .mapValues { (_, value) -> (value as? JsonPrimitive)?.jsonPrimitive?.contentOrNull }
