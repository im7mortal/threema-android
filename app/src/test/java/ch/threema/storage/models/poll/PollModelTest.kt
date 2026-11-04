package ch.threema.storage.models.poll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PollModelTest {
    @Test
    fun `serialize state`() {
        assertEquals("TEMPORARY", PollModel.State.TEMPORARY.serialized)
        assertEquals("OPEN", PollModel.State.OPEN.serialized)
        assertEquals("CLOSED", PollModel.State.CLOSED.serialized)
    }

    @Test
    fun `deserialize state`() {
        assertEquals(PollModel.State.TEMPORARY, PollModel.State.deserialize("TEMPORARY"))
        assertEquals(PollModel.State.OPEN, PollModel.State.deserialize("OPEN"))
        assertEquals(PollModel.State.CLOSED, PollModel.State.deserialize("CLOSED"))
        assertNull(PollModel.State.deserialize("unknown"))
    }

    @Test
    fun `serialize assessment`() {
        assertEquals("SINGLE_CHOICE", PollModel.Assessment.SINGLE_CHOICE.serialized)
        assertEquals("MULTIPLE_CHOICE", PollModel.Assessment.MULTIPLE_CHOICE.serialized)
    }

    @Test
    fun `deserialize assessment`() {
        assertEquals(PollModel.Assessment.SINGLE_CHOICE, PollModel.Assessment.deserialize("SINGLE_CHOICE"))
        assertEquals(PollModel.Assessment.MULTIPLE_CHOICE, PollModel.Assessment.deserialize("MULTIPLE_CHOICE"))
        assertNull(PollModel.Assessment.deserialize("unknown"))
    }

    @Test
    fun `serialize type`() {
        assertEquals("RESULT_ON_CLOSE", PollModel.Type.RESULT_ON_CLOSE.serialized)
        assertEquals("INTERMEDIATE", PollModel.Type.INTERMEDIATE.serialized)
    }

    @Test
    fun `deserialize type`() {
        assertEquals(PollModel.Type.RESULT_ON_CLOSE, PollModel.Type.deserialize("RESULT_ON_CLOSE"))
        assertEquals(PollModel.Type.INTERMEDIATE, PollModel.Type.deserialize("INTERMEDIATE"))
        assertNull(PollModel.Type.deserialize("unknown"))
    }

    @Test
    fun `serialize choice type`() {
        assertEquals("TEXT", PollModel.ChoiceType.TEXT.serialized)
    }

    @Test
    fun `deserialize choice type`() {
        assertEquals(PollModel.ChoiceType.TEXT, PollModel.ChoiceType.deserialize("TEXT"))
        assertNull(PollModel.ChoiceType.deserialize("unknown"))
    }

    @Test
    fun `serialize display type`() {
        assertEquals("LIST_MODE", PollModel.DisplayType.LIST_MODE.serialized)
        assertEquals("SUMMARY_MODE", PollModel.DisplayType.SUMMARY_MODE.serialized)
    }

    @Test
    fun `deserialize display type`() {
        assertEquals(PollModel.DisplayType.LIST_MODE, PollModel.DisplayType.deserialize("LIST_MODE"))
        assertEquals(PollModel.DisplayType.SUMMARY_MODE, PollModel.DisplayType.deserialize("SUMMARY_MODE"))
        assertNull(PollModel.DisplayType.deserialize("unknown"))
    }
}
