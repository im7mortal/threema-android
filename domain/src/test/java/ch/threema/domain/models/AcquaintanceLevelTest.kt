package ch.threema.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class AcquaintanceLevelTest {
    @Test
    fun `acquaintance level ordinal does not change`() {
        assertEquals(0, AcquaintanceLevel.DIRECT.ordinal)
        assertEquals(1, AcquaintanceLevel.GROUP_OR_DELETED.ordinal)
    }
}
