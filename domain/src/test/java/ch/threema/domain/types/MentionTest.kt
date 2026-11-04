package ch.threema.domain.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MentionTest {

    @Test
    fun `identity mention`() {
        val mention = Mention.Identity(Identity("ABCD1234"))
        assertEquals(Identity("ABCD1234"), mention.identity)
        assertEquals("ABCD1234", mention.identityString)
        assertEquals("@[ABCD1234]", mention.toString())
        assertTrue(mention.includes(Identity("ABCD1234")))
        assertFalse(mention.includes(Identity("ABCD1235")))
    }

    @Test
    fun `all users mention`() {
        val mention = Mention.AllUsers
        assertEquals("@[@@@@@@@@]", mention.toString())
        assertTrue(mention.includes(Identity("ABCD1234")))
    }

    @Test
    fun `mention mention equality`() {
        assertEquals(
            Mention.AllUsers,
            Mention.AllUsers,
        )
        assertEquals(
            Mention.Identity(Identity("ABCD1234")),
            Mention.Identity(Identity("ABCD1234")),
        )
        assertNotEquals(
            Mention.Identity(Identity("ABCD1234")),
            Mention.Identity(Identity("ABCD1235")),
        )
    }

    @Test
    fun `parse encoded mention`() {
        assertNull(Mention.parse("ABCDEFGH"))
        assertNull(Mention.parse("@[ABCD123]"))
        assertNull(Mention.parse("@[ABCD1234"))
        assertNull(Mention.parse("@[ABCD1234] "))
        assertNull(Mention.parse("@[ABCD12345]"))
        assertNull(Mention.parse("@[**000000]"))

        assertEquals(
            Mention.Identity(Identity("ABCD1234")),
            Mention.parse("@[ABCD1234]"),
        )
        assertEquals(
            Mention.Identity(Identity("*ABCD123")),
            Mention.parse("@[*ABCD123]"),
        )
        assertEquals(
            Mention.AllUsers,
            Mention.parse("@[@@@@@@@@]"),
        )
    }

    @Test
    fun `find matches`() {
        val input = """
            Hello @[ABCD1234], hello @[@@@@@@@@].
            How are you, @[ABCD1234]? How are @[EFGH5678]@[*IJKL000]?
        """.trimIndent()

        val matches = Mention.findMatches(input).toList()

        assertEquals(
            listOf(
                Mention.Match(
                    mention = Mention.Identity(Identity("ABCD1234")),
                    startIndex = 6,
                ),
                Mention.Match(
                    mention = Mention.AllUsers,
                    startIndex = 25,
                ),
                Mention.Match(
                    mention = Mention.Identity(Identity("ABCD1234")),
                    startIndex = 51,
                ),
                Mention.Match(
                    mention = Mention.Identity(Identity("EFGH5678")),
                    startIndex = 72,
                ),
                Mention.Match(
                    mention = Mention.Identity(Identity("*IJKL000")),
                    startIndex = 83,
                ),
            ),
            matches,
        )
        assertEquals(17, matches[0].endIndex)
        assertEquals(36, matches[1].endIndex)
        assertEquals(62, matches[2].endIndex)
        assertEquals(83, matches[3].endIndex)
        assertEquals(94, matches[4].endIndex)
    }

    @Test
    fun `strip mentions`() {
        val input = "Hello @[ABCD1234], @[@@@@@@@@]@[*ABCD123].@[xyz]"
        val result = Mention.stripMentions(input)
        assertEquals(
            "Hello , .@[xyz]",
            result,
        )
    }
}
