package ch.threema.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NicknameTest {
    @Test
    fun `nicknames are truncated to 32 bytes`() {
        val nickname1 = Nickname("John Smith")
        assertEquals("John Smith", nickname1.nickname)

        val nickname2 = Nickname("Johnathan Smith with a very long name")
        assertEquals("Johnathan Smith with a very long", nickname2.nickname)
    }

    @Test
    fun `nickname equality`() {
        assertEquals(Nickname("John"), Nickname("John"))
        assertNotEquals(Nickname("John"), Nickname("john"))
    }

    @Test
    fun `nickname string representation`() {
        assertEquals("John Smith", Nickname("John Smith").toString())
    }
}
