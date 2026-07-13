package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonArrayIteratorTest {

    @Test
    fun `iterate through mixed type json array`() {
        val input = """[1,"abc",true,null,{"a":"b"},null]"""
        val iterator = JsonArrayIterator(input)
        assertTrue(iterator.hasNext())
        assertEquals(1, iterator.nextInt())
        assertTrue(iterator.hasNext())
        assertEquals("abc", iterator.nextString())
        assertTrue(iterator.hasNext())
        assertEquals(true, iterator.nextBoolean())
        assertTrue(iterator.hasNext())
        assertEquals(null, iterator.nextString())
        assertTrue(iterator.hasNext())
        assertEquals(mapOf("a" to "b"), iterator.nextPrimitiveValueMap())
        assertTrue(iterator.hasNext())
        assertEquals(null, iterator.nextPrimitiveValueMap())
        assertFalse(iterator.hasNext())
    }

    @Test
    fun `mixed type maps`() {
        val input = """[{"a":"string","b":1,"c":true,"d":null,"e":[],"f":{}}]"""
        val iterator = JsonArrayIterator(input)
        assertEquals(
            mapOf<String, Any?>(
                "a" to "string",
                // there is no explicit int type in JSON, only number
                "b" to 1.0,
                "c" to true,
                "d" to null,
                // nested JSON arrays are not supported
                "e" to null,
                // nested JSON objects are not supported
                "f" to null,
            ),
            iterator.nextPrimitiveValueMap(),
        )
    }

    @Test
    fun `iterating past the end throws an exception`() {
        assertFailsWith<NoSuchElementException> {
            JsonArrayIterator("[]").nextInt()
        }
        assertFailsWith<NoSuchElementException> {
            JsonArrayIterator("[]").nextString()
        }
        assertFailsWith<NoSuchElementException> {
            JsonArrayIterator("[]").nextBoolean()
        }
        assertFailsWith<NoSuchElementException> {
            JsonArrayIterator("[]").nextPrimitiveValueMap()
        }
    }

    @Test
    fun `type coercion`() {
        assertEquals(true, JsonArrayIterator("""["true"]""").nextBoolean())
        assertEquals(123, JsonArrayIterator("""["123"]""").nextInt())
    }

    @Test
    fun `invalid type throws an exception`() {
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("[true]").nextInt()
        }
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("[{}]").nextString()
        }
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("[42]").nextString()
        }
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("[false]").nextString()
        }
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("[123]").nextBoolean()
        }
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("""[""]""").nextBoolean()
        }
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("""[1]""").nextPrimitiveValueMap()
        }
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("""[false]""").nextPrimitiveValueMap()
        }
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("""[""]""").nextPrimitiveValueMap()
        }
        assertFailsWith<IllegalArgumentException> {
            JsonArrayIterator("""[[]]""").nextPrimitiveValueMap()
        }
    }
}
