package ch.threema.data.datatypes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationCategoryTest {
    @Test
    fun `serialize conversation categories`() {
        assertEquals(0, ConversationCategory.DEFAULT.serializedValue)
        assertEquals(1, ConversationCategory.PROTECTED.serializedValue)
    }

    @Test
    fun `deserialize conversation category`() {
        assertEquals(ConversationCategory.DEFAULT, ConversationCategory.deserialize(0))
        assertEquals(ConversationCategory.PROTECTED, ConversationCategory.deserialize(1))
    }

    @Test
    fun `deserialize invalid conversation category values returns null`() {
        assertNull(ConversationCategory.deserialize(-1))
        assertNull(ConversationCategory.deserialize(2))
    }
}
