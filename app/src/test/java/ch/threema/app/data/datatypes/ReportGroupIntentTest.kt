package ch.threema.app.data.datatypes

import ch.threema.app.groupflows.ReportGroupIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportGroupIntentTest {

    @Test
    fun toSerializedValue() {
        assertEquals(0, ReportGroupIntent.REPORT_GROUP.serializedValue)
        assertEquals(1, ReportGroupIntent.REPORT_GROUP_BLOCK_CREATOR_DELETE_GROUP.serializedValue)
    }

    @Test
    fun fromSerializedValue() {
        assertEquals(
            expected = ReportGroupIntent.REPORT_GROUP,
            actual = ReportGroupIntent.fromSerializedValue(0),
        )
        assertEquals(
            expected = ReportGroupIntent.REPORT_GROUP_BLOCK_CREATOR_DELETE_GROUP,
            actual = ReportGroupIntent.fromSerializedValue(1),
        )
        assertNull(
            actual = ReportGroupIntent.fromSerializedValue(2),
        )
        assertNull(
            actual = ReportGroupIntent.fromSerializedValue(-1),
        )
    }
}
