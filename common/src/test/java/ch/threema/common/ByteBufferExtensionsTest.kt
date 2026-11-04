package ch.threema.common

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class ByteBufferExtensionsTest {

    @Test
    fun `create direct byte buffer from char array`() {
        val byteBuffer = charArrayOf('A', 'B', 'C').toDirectByteBuffer()

        assertTrue(byteBuffer.isDirect)
        assertContentEquals(byteArrayOf('A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte()), byteBuffer.toByteArray())
    }

    @Test
    fun `create direct byte buffer from string`() {
        val byteBuffer = "Hello Wörld".toDirectByteBuffer()

        assertTrue(byteBuffer.isDirect)
        assertContentEquals("Hello Wörld".toByteArray(), byteBuffer.toByteArray())
    }

    @Test
    fun `create direct byte buffer from byte array`() {
        val byteBuffer = byteArrayOf(1, 2, 3, 4).toDirectByteBuffer()

        assertTrue(byteBuffer.isDirect)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), byteBuffer.toByteArray())
    }

    @Test
    fun `create direct byte buffer from concatenated byte arrays`() {
        val byteBuffer = concatenateByteArraysToDirectByteBuffer(byteArrayOf(1, 2), byteArrayOf(3, 4, 5))

        assertTrue(byteBuffer.isDirect)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), byteBuffer.toByteArray())
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val byteArray = ByteArray(capacity())
        get(byteArray)
        return byteArray
    }
}
