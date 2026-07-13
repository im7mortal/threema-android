package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByteSizeTest {

    @Test
    fun `orders of magnitude`() {
        assertEquals(1, 1.bytes.bytes)
        assertEquals(1, 1L.bytes.bytes)
        assertEquals(1000, 1.kiloBytes.bytes)
        assertEquals(1000, 1L.kiloBytes.bytes)
    }

    @Test
    fun `comparing sizes`() {
        assertTrue(3.bytes > 2.bytes)
        assertEquals(42.bytes, 42.bytes)
        assertTrue(1.kiloBytes < 1001.bytes)
    }

    @Test
    fun `arithmetic operations`() {
        assertEquals(8.bytes, 10.bytes + 6.bytes - 8.bytes)
    }

    @Test
    fun factors() {
        assertEquals(8.bytes, 4 * 2.bytes)
        assertEquals(100.bytes, 10.bytes * 10)
    }

    @Test
    fun `byte size ratios`() {
        assertEquals(1.0, 1000.bytes / 1.kiloBytes)
        assertEquals(0.25, 64.kiloBytes / 256.kiloBytes)
    }

    @Test
    fun `string representation`() {
        assertEquals("1024 bytes", 1024.bytes.toString())
    }
}
