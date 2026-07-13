package ch.threema.app.voip.util

import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals

class CallIdGeneratorTest {

    @Test
    fun `generate call id`() {
        val generator = CallIdGenerator(secureRandom = mockRandom(123))
        assertEquals(123L, generator.generateCallId())
    }

    @Test
    fun `call id is never negative`() {
        val generator = CallIdGenerator(secureRandom = mockRandom(-0xFA))
        assertEquals(0xFFFFFF06L, generator.generateCallId())
    }

    @Test
    fun `call id is always within valid range`() {
        val generator = CallIdGenerator(secureRandom = mockRandom(Int.MAX_VALUE))
        assertEquals(0x7FFFFFFFL, generator.generateCallId())
    }

    companion object {
        private fun mockRandom(nextInt: Int) =
            mockk<SecureRandom> {
                every { nextInt() } returns nextInt
            }
    }
}
