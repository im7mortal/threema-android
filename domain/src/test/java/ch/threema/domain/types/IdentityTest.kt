package ch.threema.domain.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityTest {
    @Test
    fun `to string`() {
        assertEquals(
            "*ANDROID",
            Identity("*ANDROID").toString(),
        )
    }

    @Test
    fun `get value`() {
        assertEquals(
            "TESTTEST",
            Identity("TESTTEST").value,
        )
    }

    @Test
    fun equality() {
        assertEquals(
            Identity("TESTTEST"),
            Identity("TESTTEST"),
        )
    }

    @Test
    fun inequality() {
        assertNotEquals(
            Identity("TEST1234"),
            Identity("TEST5678"),
        )
    }

    @Test
    fun `valid identities`() {
        Identity("01234567")
        Identity("ABCDEFGH")
        Identity("IJKLMNOP")
        Identity("QRSTUVWX")
        Identity("YZ987654")
        Identity("*ANDROID")
    }

    @Test
    fun `invalid identities`() {
        assertIsInvalidIdentity("")
        assertIsInvalidIdentity("        ")
        assertIsInvalidIdentity("1234567")
        assertIsInvalidIdentity("123456789")
        assertIsInvalidIdentity("ABCDEFGh")
        assertIsInvalidIdentity("ÄBCDËFGH")
        assertIsInvalidIdentity("qrstuvwx")
        assertIsInvalidIdentity("AND*ROID")
        assertIsInvalidIdentity("@ABCDEFG")
        assertIsInvalidIdentity("@@@@@@@@")
    }

    @Test
    fun `to identity or null if invalid`() {
        assertEquals(
            Identity("01234567"),
            "01234567".toIdentityOrNull(),
        )
        assertNull("".toIdentityOrNull())
    }

    @Test
    fun `test validity of identity strings`() {
        assertTrue(Identity.isValidIdentity("01234567"))
        assertTrue(Identity.isValidIdentity("ABCD1234"))
        assertTrue(Identity.isValidIdentity("*ANDROID"))

        assertFalse(Identity.isValidIdentity(""))
        assertFalse(Identity.isValidIdentity("ANDROID*"))
        assertFalse(Identity.isValidIdentity("ABCD12345"))
        assertFalse(Identity.isValidIdentity("ABCD123"))
        assertFalse(Identity.isValidIdentity("abcd1234"))
        assertFalse(Identity.isValidIdentity("ÄBCD1234"))
        assertFalse(Identity.isValidIdentity("@@@@@@@@"))
    }

    private fun assertIsInvalidIdentity(string: String) {
        assertFailsWith<IllegalArgumentException> {
            Identity(string)
        }
    }
}
