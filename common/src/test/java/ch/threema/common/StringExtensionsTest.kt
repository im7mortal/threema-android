package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringExtensionsTest {
    @Test
    fun `string without last line`() {
        assertEquals("", "Hello World".withoutLastLine())
        assertEquals("Hello", "Hello\nWorld".withoutLastLine())
        assertEquals("Hello\nWorld", "Hello\nWorld\n".withoutLastLine())
        assertEquals("", "".withoutLastLine())
    }

    @Test
    fun `last line of string`() {
        assertEquals("Hello World", "Hello World".lastLine())
        assertEquals("World", "Hello\nWorld".lastLine())
        assertEquals("", "Hello\nWorld\n".lastLine())
        assertEquals("", "".lastLine())
    }

    @Test
    fun `replace last`() {
        assertEquals("Hello Beautiful World", "Hello Hello World".replaceLast("Hello", "Beautiful"))
        assertEquals("abc.def.abc.ghi", "abc.def.abc.def".replaceLast("def", "ghi"))
        assertEquals("Hello", "Hello".replaceLast("World", "Moon"))
    }

    @Test
    fun `take string unless empty`() {
        assertEquals("test", "test".takeUnlessEmpty())
        assertNull("".takeUnlessEmpty())
    }

    @Test
    fun `take string unless blank`() {
        assertEquals("test", "test".takeUnlessBlank())
        assertEquals(" test ", " test ".takeUnlessBlank())
        assertEquals(" te st ", " te st ".takeUnlessBlank())
        assertNull("".takeUnlessBlank())
        assertNull("  ".takeUnlessBlank())
    }

    @Test
    fun `test capitalize`() {
        assertEquals("Hello worlD", "hello worlD".capitalize())
        assertEquals("Hello World", "Hello World".capitalize())
        assertEquals("Äöü", "äöü".capitalize())
    }

    @Test
    fun `without linebreaks`() {
        assertEquals("abcd efgh", "\n\nabc\rd e\r\n\r\nfg\r\rh\n".withoutLineBreaks(replaceWith = ""))
        assertEquals("Hello world ", "Hello\r\nworld\n".withoutLineBreaks(replaceWith = " "))
    }

    @Test
    fun `truncate UTF-8 strings`() {
        assertEquals(
            "0000000000111111111122222222223",
            "0000000000111111111122222222223".truncateUTF8String(32),
        )
        assertEquals(
            "hello my best friend",
            "hello my best friend".truncateUTF8String(32),
        )
        assertEquals(
            "coco",
            "coco".truncateUTF8String(4),
        )

        // with multibyte characters
        assertEquals(
            "0000000000111111111122222222223",
            "0000000000111111111122222222223Ç".truncateUTF8String(32),
        )
        assertEquals(
            "Aj aj aj Çoc",
            "Aj aj aj Çoco Jambo".truncateUTF8String(13),
        )
        assertEquals(
            "Çoc",
            "Çoco Jambo".truncateUTF8String(4),
        )
    }
}
