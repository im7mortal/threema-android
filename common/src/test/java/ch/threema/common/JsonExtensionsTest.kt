package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.json.buildJsonArray
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class JsonExtensionsTest {
    @Test
    fun `JSONArray to IntArray`() {
        val jsonArray = JSONArray("[1, 3, 42]")

        assertContentEquals(
            intArrayOf(1, 3, 42),
            jsonArray.toIntArray(),
        )
    }

    @Test
    fun `JSONArray to IntArray, with invalid data`() {
        val jsonArray = JSONArray("[1, 3, \"not a number\"]")

        assertFailsWith<JSONException> {
            jsonArray.toIntArray()
        }
    }

    @Test
    fun `JSONArray to list of JSONObject`() {
        val jsonArray = JSONArray("""[{}, {"key": "value"}]""")

        assertContentEquals(
            listOf(
                JSONObject().toString(),
                JSONObject("""{"key": "value"}""").toString(),
            ),
            jsonArray.toJSONObjectList().map { it.toString() },
        )
    }

    @Test
    fun `JSONArray to list of JSONObject, with invalid data`() {
        val jsonArray = JSONArray("""[{}, 42]""")

        assertFailsWith<JSONException> {
            jsonArray.toJSONObjectList()
        }
    }

    @Test
    fun `get string or null`() {
        val jsonObject = JSONObject()
        jsonObject.put("A", "Hello")

        assertEquals("Hello", jsonObject.getStringOrNull("A"))
        assertNull(jsonObject.getStringOrNull("B"))
    }

    @Test
    fun `add map as json object to json array`() {
        val jsonArray = buildJsonArray {
            addObject(
                mapOf(
                    "A" to 1,
                    "B" to 2L,
                    "C" to 3.3f,
                    "D" to 4.4,
                    "E" to true,
                    "F" to null,
                    "G" to "Hello",
                    "H" to object : Any() {
                        override fun toString() = "World"
                    },
                    "I" to listOf("a", "b", "c"),
                ),
            )
        }
        assertEquals(
            """[{"A":1,"B":2,"C":3.3,"D":4.4,"E":true,"F":null,"G":"Hello","H":"World","I":"[a, b, c]"}]""",
            jsonArray.toString(),
        )
    }

    @Test
    fun `parse json object as map`() {
        val input = """{"a":"A","b":123,"c":null,"d":[],"e":false, "f":{}, "g":"G"}"""
        assertEquals(
            mapOf("a" to "A", "b" to "123", "c" to null, "d" to null, "e" to "false", "f" to null, "g" to "G"),
            parseJsonObjectAsStringMap(input),
        )
    }
}
