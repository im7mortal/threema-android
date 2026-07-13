package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base64Test {
    @Test
    fun `encode base64`() {
        assertEquals("", Base64.encode(emptyByteArray()))
        assertEquals("Ag==", Base64.encode(byteArrayOf(2)))
        assertEquals("AQIDBAX/", Base64.encode(byteArrayOf(1, 2, 3, 4, 5, 0xff.toByte())))
    }

    @Test
    fun `decode base64`() {
        assertContentEquals(emptyByteArray(), Base64.decode(""))
        assertContentEquals(byteArrayOf(2), Base64.decode("Ag=="))
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 0xff.toByte()), Base64.decode("AQIDBAX/"))
        assertContentEquals(
            byteArrayOf(
                -1, 105, -22, 47, 109, 75, -90, -9, 21, -74, -114, 34, 82, -41, 107, 107, -103, -21,
                87, -79, -112, 4, 73, 102, 107, -29, 95, -15, 14, -34, -94, 67, -60, 96, 65, 57, -116,
            ),
            Base64.decode("/2nqL21LpvcVto4iUtdra5nrV7GQBElma+Nf8Q7eokPEYEE5jA=="),
        )
    }

    @Test
    fun `decode url-safe base64`() {
        assertContentEquals(emptyByteArray(), Base64.UrlSafe.decode(""))
        assertContentEquals(byteArrayOf(2), Base64.UrlSafe.decode("Ag=="))
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 0xff.toByte()), Base64.UrlSafe.decode("AQIDBAX_"))
        assertContentEquals(
            byteArrayOf(
                -1, 105, -22, 47, 109, 75, -90, -9, 21, -74, -114, 34, 82, -41, 107, 107, -103, -21,
                87, -79, -112, 4, 73, 102, 107, -29, 95, -15, 14, -34, -94, 67, -60, 96, 65, 57, -116,
            ),
            Base64.UrlSafe.decode("_2nqL21LpvcVto4iUtdra5nrV7GQBElma-Nf8Q7eokPEYEE5jA=="),
        )
    }

    @Test
    fun `decode base64 with insufficient padding`() {
        assertContentEquals(byteArrayOf(2), Base64.decode("Aq=="))
    }

    @Test
    fun `decode invalid base64`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            assertContentEquals(byteArrayOf(2), Base64.decode("dummy"))
        }
        assertContains(exception.message!!, "dummy")
    }
}
