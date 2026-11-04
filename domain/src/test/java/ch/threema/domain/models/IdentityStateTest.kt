package ch.threema.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityStateTest {
    @Test
    fun `identity state value does not change`() {
        assertEquals(0, IdentityState.ACTIVE.value)
        assertEquals(1, IdentityState.INACTIVE.value)
        assertEquals(2, IdentityState.INVALID.value)
    }
}
