package ch.threema.app.license

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsernameParameterTest {

    private val source = ParameterSource.USER

    @Test
    fun `create returns null for empty string`() {
        assertNull(UsernameParameter.create("", source))
    }

    @Test
    fun `create returns parameter with the given username unchanged`() {
        val result = UsernameParameter.create("johndoe", source)
        assertEquals("johndoe", result?.value)
    }

    @Test
    fun `create does not trim whitespace around the username`() {
        val result = UsernameParameter.create("  johndoe  ", source)
        assertEquals("  johndoe  ", result?.value)
    }

    @Test
    fun `create treats a whitespace only string as valid since it is not empty`() {
        val result = UsernameParameter.create("   ", source)
        assertEquals("   ", result?.value)
    }

    @Test
    fun `create preserves special characters in username`() {
        val result = UsernameParameter.create("john.doe+test@example.com", source)
        assertEquals("john.doe+test@example.com", result?.value)
    }

    @Test
    fun `create preserves a single character username`() {
        val result = UsernameParameter.create("a", source)
        assertEquals("a", result?.value)
    }

    @Test
    fun `create sets the provided parameter source`() {
        val result = UsernameParameter.create("johndoe", source)
        assertEquals(source, result?.source)
    }
}
