package ch.threema.data.datatypes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationVisibilityTest {
    @Test
    fun `serialize conversation visibilities`() {
        assertEquals(0, ConversationVisibility.NORMAL.serializedValue)
        assertEquals(1, ConversationVisibility.ARCHIVED.serializedValue)
        assertEquals(2, ConversationVisibility.PINNED.serializedValue)
    }

    @Test
    fun `deserialize conversation visibilities`() {
        assertEquals(ConversationVisibility.NORMAL, ConversationVisibility.deserialize(0))
        assertEquals(ConversationVisibility.ARCHIVED, ConversationVisibility.deserialize(1))
        assertEquals(ConversationVisibility.PINNED, ConversationVisibility.deserialize(2))
    }

    @Test
    fun `deserialize invalid conversation visibility values returns null`() {
        assertNull(ConversationVisibility.deserialize(-1))
        assertNull(ConversationVisibility.deserialize(3))
    }
}
