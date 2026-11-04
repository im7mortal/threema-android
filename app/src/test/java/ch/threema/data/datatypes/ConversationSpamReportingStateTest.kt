package ch.threema.data.datatypes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationSpamReportingStateTest {

    @Test
    fun toSerializedValue() {
        assertEquals(0, ConversationSpamReportingState.OPEN.serializedValue)
        assertEquals(1, ConversationSpamReportingState.NOT_SPAM.serializedValue)
        assertEquals(2, ConversationSpamReportingState.REPORTED.serializedValue)
    }

    @Test
    fun fromSerializedValue() {
        assertEquals(
            expected = ConversationSpamReportingState.OPEN,
            actual = ConversationSpamReportingState.fromSerializedValue(0),
        )
        assertEquals(
            expected = ConversationSpamReportingState.NOT_SPAM,
            actual = ConversationSpamReportingState.fromSerializedValue(1),
        )
        assertEquals(
            expected = ConversationSpamReportingState.REPORTED,
            actual = ConversationSpamReportingState.fromSerializedValue(2),
        )
        assertNull(
            actual = ConversationSpamReportingState.fromSerializedValue(3),
        )
        assertNull(
            actual = ConversationSpamReportingState.fromSerializedValue(-1),
        )
    }
}
