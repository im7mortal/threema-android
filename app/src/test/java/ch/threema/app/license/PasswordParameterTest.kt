package ch.threema.app.license

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PasswordParameterTest {

    private val source = ParameterSource.USER

    @Test
    fun `create returns null for empty string`() {
        assertNull(PasswordParameter.create("", source))
    }

    @Test
    fun `create returns parameter with the given password unchanged`() {
        val result = PasswordParameter.create("secret2", source)
        assertEquals("secret2", result?.value)
    }

    @Test
    fun `create does not trim whitespace around the password`() {
        val result = PasswordParameter.create("  secret2  ", source)
        assertEquals("  secret2  ", result?.value)
    }

    @Test
    fun `create treats a whitespace only string as valid since it is not empty`() {
        val result = PasswordParameter.create("   ", source)
        assertEquals("   ", result?.value)
    }

    @Test
    fun `create preserves special characters in password`() {
        val result = PasswordParameter.create("P@ssw0rd!#\$%^&*()", source)
        assertEquals("P@ssw0rd!#\$%^&*()", result?.value)
    }

    @Test
    fun `create preserves a single character password`() {
        val result = PasswordParameter.create("a", source)
        assertEquals("a", result?.value)
    }

    @Test
    fun `create sets the provided parameter source`() {
        val result = PasswordParameter.create("secret2", source)
        assertEquals(source, result?.source)
    }
}
