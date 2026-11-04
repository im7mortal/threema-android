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

    @Test
    fun `sum of sizes of empty collection`() {
        assertEquals(0, emptyList<List<Unit>>().sumOfSizes())
    }

    @Test
    fun `sum of sizes with non-empty collection`() {
        assertEquals(
            expected = 10,
            actual = listOf(
                listOf(1, 2, 3),
                listOf(4, 5, 6, 7, 8, 9),
                listOf(10),
                emptyList(),
            ).sumOfSizes(),
        )
    }

    @Test
    fun `sum of sizes with collection containing empty lists`() {
        assertEquals(
            expected = 0,
            actual = listOf<List<Unit>>(
                emptyList(),
                emptyList(),
                emptyList(),
            ).sumOfSizes(),
        )
    }

    @Test
    fun `for each reversed`() {
        val result = buildList {
            listOf(1, 2, 3, 4, 5).forEachReversed {
                add(it)
            }
        }
        assertEquals(
            listOf(5, 4, 3, 2, 1),
            result,
        )
    }
}
