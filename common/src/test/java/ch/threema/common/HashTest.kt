package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertContentEquals

class HashTest {

    @Test
    fun `hashing sha256`() {
        assertContentEquals(
            byteArrayOf(
                -29, -80, -60, 66, -104, -4, 28, 20, -102, -5, -12, -56, -103, 111, -71, 36,
                39, -82, 65, -28, 100, -101, -109, 76, -92, -107, -103, 27, 120, 82, -72, 85,
            ),
            sha256(""),
        )
        assertContentEquals(
            byteArrayOf(
                -29, -80, -60, 66, -104, -4, 28, 20, -102, -5, -12, -56, -103, 111, -71, 36,
                39, -82, 65, -28, 100, -101, -109, 76, -92, -107, -103, 27, 120, 82, -72, 85,
            ),
            sha256(emptyByteArray()),
        )
        assertContentEquals(
            byteArrayOf(
                -43, 21, -103, 113, -102, 70, -114, -111, 87, -82, -113, 23, -35, -92, 46, 14,
                -33, -63, 113, -25, 104, 29, -120, 100, -112, 95, 12, -8, 118, -76, -49, -59,
            ),
            sha256("*1234567"),
        )
        assertContentEquals(
            byteArrayOf(
                -43, 21, -103, 113, -102, 70, -114, -111, 87, -82, -113, 23, -35, -92, 46, 14,
                -33, -63, 113, -25, 104, 29, -120, 100, -112, 95, 12, -8, 118, -76, -49, -59,
            ),
            sha256("*1234567".toByteArray(charset = Charsets.UTF_8)),
        )
    }
}
