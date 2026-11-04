package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DatabaseHelpersTest {
    @Test
    fun `convert items to selection args`() {
        assertContentEquals(
            arrayOf("1", "2", "3"),
            DatabaseHelpers.convertToSelectionArgs(listOf(1, 2, 3)),
        )
        assertContentEquals(
            arrayOf("1!", "2!", "3!"),
            DatabaseHelpers.convertToSelectionArgs(listOf(1, 2, 3)) { "$it!" },
        )
    }

    @Test
    fun `make placeholders`() {
        assertEquals(
            "",
            DatabaseHelpers.makePlaceholders(0),
        )
        assertEquals(
            "?",
            DatabaseHelpers.makePlaceholders(1),
        )
        assertEquals(
            "?,?",
            DatabaseHelpers.makePlaceholders(2),
        )
        assertEquals(
            "?,?,?,?,?",
            DatabaseHelpers.makePlaceholders(5),
        )
    }
}
