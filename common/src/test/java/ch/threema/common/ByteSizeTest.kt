package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByteSizeTest {

    @Test
    fun `orders of magnitude`() {
        assertEquals(1, 1.bytes.bytes)
        assertEquals(1, 1L.bytes.bytes)
        assertEquals(1_000, 1.kiloBytes.bytes)
        assertEquals(1_000, 1L.kiloBytes.bytes)
        assertEquals(1_000_000, 1.megaBytes.bytes)
        assertEquals(1_000_000, 1L.megaBytes.bytes)
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
        assertEquals("0 bytes", 0.bytes.toString())
        assertEquals("1 bytes", 1.bytes.toString())
        assertEquals("512 bytes", 512.bytes.toString())
        assertEquals("1.024 kilobytes", 1024.bytes.toString())
        assertEquals("2.0 megabytes", 2000.kiloBytes.toString())
        assertEquals("2.0 megabytes", 2.megaBytes.toString())
        assertEquals("4000.001 megabytes", 4_000_001.kiloBytes.toString())
    }
}
