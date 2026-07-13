package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionExtensionsTest {
    @Test
    fun `take collection unless it is empty`() {
        assertEquals(
            setOf(1, 2, 3),
            setOf(1, 2, 3).takeUnlessEmpty(),
        )
        assertNull(
            emptyList<Int>().takeUnlessEmpty(),
        )
    }

    @Test
    fun `adding item to set with toggle`() {
        val mySet = setOf(1, 2, 3)
        assertEquals(
            setOf(1, 2, 3, 4),
            mySet.toggle(4),
        )
    }

    @Test
    fun `removing item from set with toggle`() {
        val mySet = setOf(1, 2, 3)
        assertEquals(
            setOf(1, 3),
            mySet.toggle(2),
        )
    }

    @Test
    fun `list to enumeration`() {
        val list = listOf(1, 2, 3)

        val enumeration = list.toEnumeration()

        assertTrue(enumeration.hasMoreElements())
        assertEquals(1, enumeration.nextElement())
        assertTrue(enumeration.hasMoreElements())
        assertEquals(2, enumeration.nextElement())
        assertTrue(enumeration.hasMoreElements())
        assertEquals(3, enumeration.nextElement())
        assertFalse(enumeration.hasMoreElements())
        assertFailsWith<NoSuchElementException> {
            enumeration.nextElement()
        }
    }

    @Test
    fun `empty list to enumeration`() {
        val enumeration = emptyList<Int>().toEnumeration()

        assertFalse(enumeration.hasMoreElements())
        assertFailsWith<NoSuchElementException> {
            enumeration.nextElement()
        }
    }

    @Test
    fun `put if not null`() {
        val map = buildMap {
            putIfNotNull(1, "A")
            putIfNotNull(2, null as String?)
            putIfNotNull(3, "B")
            putIfNotNull(4, null as String?)
        }
        assertEquals(
            mapOf(1 to "A", 3 to "B"),
            map,
        )
    }

    @Test
    fun `has duplicates`() {
        assertFalse(emptyList<String>().hasDuplicatesBy { it })
        assertFalse(listOf("A", "b", "c", "D").hasDuplicatesBy { it.lowercase() })
        assertTrue(listOf("A", "b", "a", "D").hasDuplicatesBy { it.lowercase() })
    }
}
