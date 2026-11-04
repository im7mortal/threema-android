package ch.threema.app.license

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SerialParameterTest {

    @Test
    fun `create returns null for null input`() {
        assertNull(SerialParameter.create(null))
    }

    @Test
    fun `create returns null for empty string`() {
        assertNull(SerialParameter.create(""))
    }

    @Test
    fun `create returns null for whitespace only string`() {
        assertNull(SerialParameter.create("   "))
    }

    @Test
    fun `create returns null for tab only string`() {
        assertNull(SerialParameter.create("\t"))
    }

    @Test
    fun `create returns null for newline only string`() {
        assertNull(SerialParameter.create("\n"))
    }

    @Test
    fun `create returns parameter with the given serial key unchanged`() {
        val result = SerialParameter.create("ABCDE-12345")
        assertEquals("ABCDE-12345", result?.value)
    }

    @Test
    fun `create does not trim whitespace around the serial key`() {
        val result = SerialParameter.create("  ABCDE-12345  ")
        assertEquals("  ABCDE-12345  ", result?.value)
    }

    @Test
    fun `create preserves a single character serial key`() {
        val result = SerialParameter.create("A")
        assertEquals("A", result?.value)
    }

    @Test
    fun `create always sets source to null`() {
        val result = SerialParameter.create("ABCDE-12345")
        assertNull(result?.source)
    }
}
