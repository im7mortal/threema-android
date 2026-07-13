package ch.threema.storage.models

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationTagTest {
    @Test
    fun `value of conversation tag matches`() {
        // As this value is persisted, it is required that this value is never changed.
        assertEquals("unread", ConversationTag.MARKED_AS_UNREAD.value)
    }
}
