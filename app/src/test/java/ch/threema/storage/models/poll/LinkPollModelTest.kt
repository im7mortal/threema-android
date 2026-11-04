package ch.threema.storage.models.poll

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkPollModelTest {
    @Test
    fun `serialize link poll model type`() {
        assertEquals("CONTACT", LinkPollModel.Type.CONTACT.serialized)
        assertEquals("GROUP", LinkPollModel.Type.GROUP.serialized)
    }
}
