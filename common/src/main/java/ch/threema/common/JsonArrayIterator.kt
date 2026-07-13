package ch.threema.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Enables parsing and iterating a mixed-value JSON array
 *
 * @param jsonString A string-encoded JSON-array
 */
class JsonArrayIterator(
    jsonString: String,
) {
    private val iterator: Iterator<JsonElement> = Json.decodeFromString<JsonArray>(jsonString).iterator()

    /**
     * Reads the next element of the JSON array and treats it as an int
     *
     * @throws IllegalArgumentException if the next element is not an int and can not be coerced into one
     * @throws NoSuchElementException if there is no next element
     */
    fun nextInt(): Int =
        iterator.next().intOrNull ?: throw IllegalArgumentException("Not an int")

    /**
     * Reads the next element of the JSON array and treats it as a boolean
     *
     * @throws IllegalArgumentException if the next element is not a boolean and can not be coerced into one
     * @throws NoSuchElementException if there is no next element
     */
    fun nextBoolean(): Boolean =
        iterator.next().booleanOrNull ?: throw IllegalArgumentException("Not a boolean")

    /**
     * Reads the next element of the JSON array and treats it as a (nullable) string
     *
     * @throws IllegalArgumentException if the next element is not a string
     * @throws NoSuchElementException if there is no next element
     */
    fun nextString(): String? {
        val element = iterator.next()
        if (element is JsonNull) {
            return null
        }
        val primitive = element.jsonPrimitive
        if (primitive.isString) {
            return primitive.content
        }
        throw IllegalArgumentException("Not a string")
    }

    /**
     * Reads the next element of the JSON array and treats it as (nullable) object, converting it to a map.
     * Non-primitive values will be converted to null.
     *
     * @throws IllegalArgumentException if the next element is not a JSON object
     * @throws NoSuchElementException if there is no next element
     */
    fun nextPrimitiveValueMap(): Map<String, Any?>? {
        val element = iterator.next()
        if (element is JsonNull) {
            return null
        }
        return element.jsonObject.mapValues { (_, value) ->
            if (value !is JsonPrimitive || value is JsonNull) {
                return@mapValues null
            }
            if (value.isString) {
                return@mapValues value.content
            }
            value.content.toDoubleOrNull()
                ?: value.content.toBooleanStrictOrNull()
        }
    }

    fun hasNext(): Boolean =
        iterator.hasNext()
}
