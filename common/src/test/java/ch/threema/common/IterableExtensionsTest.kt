package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals

class IterableExtensionsTest {
    @Test
    fun `associate with not null keeps the order`() {
        // Arrange
        val numbers = (0..9)

        // Act
        val evenNumbers = numbers.associateWithNotNull { number ->
            if (number % 2 == 0) {
                number
            } else {
                null
            }
        }

        // Assert. Note that we convert the entries to a list to check that the order is preserved
        val expected = (0..9 step 2).associateWith { it }.toList()
        assertEquals(expected, evenNumbers.toList())
    }

    @Test
    fun `associate with not null keeps last element`() {
        // Arrange
        val numbers = listOf(0, 1, 0)

        // Act
        var mappedValue = 0
        val associated = numbers.associateWith { mappedValue++ }

        // Assert
        val expected = mapOf(
            0 to 2,
            1 to 1,
        )

        assertEquals(expected, associated)
    }

    @Test
    fun `empty list yields nothing`() {
        val result = emptyList<Int>().repeatingLast().take(5).toList()
        assertEquals(emptyList(), result)
    }

    @Test
    fun `single element repeats forever`() {
        val result = listOf(42).repeatingLast().take(5).toList()
        assertEquals(listOf(42, 42, 42, 42, 42), result)
    }

    @Test
    fun `multiple elements are emitted in order then last one repeats`() {
        val result = listOf(1, 2, 3).repeatingLast().take(6).toList()
        assertEquals(listOf(1, 2, 3, 3, 3, 3), result)
    }

    @Test
    fun `exact list size does not trigger repetition yet`() {
        val result = listOf("a", "b", "c").repeatingLast().take(3).toList()
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `works with a Set as the source Iterable`() {
        val source = setOf(1, 2, 3)
        val result = source.repeatingLast().take(5).toList()
        val expectedLast = source.last()
        assertEquals(expectedLast, result[3])
        assertEquals(expectedLast, result[4])
    }

    @Test
    fun `does not evaluate source eagerly`() {
        var evaluatedCount = 0
        val lazySource = object : Iterable<Int> {
            override fun iterator(): Iterator<Int> = object : Iterator<Int> {
                var i = 0
                override fun hasNext() = i < 3
                override fun next(): Int {
                    evaluatedCount++
                    return ++i
                }
            }
        }

        val iterator = lazySource.repeatingLast().iterator()
        assertEquals(0, evaluatedCount) // nothing pulled yet
        iterator.next()
        assertEquals(1, evaluatedCount)
    }

    @Test
    fun `null last element repeats correctly`() {
        val result = listOf(1, 2, null).repeatingLast().take(5).toList()
        assertEquals(listOf(1, 2, null, null, null), result)
    }

    @Test
    fun `list of all nulls repeats null forever`() {
        val result = listOf(null, null).repeatingLast().take(4).toList()
        assertEquals(listOf(null, null, null, null), result)
    }

    @Test
    fun `single null element repeats forever`() {
        val result = listOf<Int?>(null).repeatingLast().take(3).toList()
        assertEquals(listOf(null, null, null), result)
    }

    @Test
    fun `null element does not abort iteration`() {
        val result = listOf(1, null, 2).repeatingLast().take(5).toList()
        assertEquals(listOf(1, null, 2, 2, 2), result)
    }

    @Test
    fun `empty list of nullable type yields nothing`() {
        val result = emptyList<Int?>().repeatingLast().take(5).toList()
        assertEquals(emptyList(), result)
    }
}
