package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThrowableExtensionsTest {
    @Test
    fun `has cause`() {
        val exception = RuntimeException(
            IllegalArgumentException(
                IllegalStateException(),
            ),
        )
        assertTrue(exception.hasInCauseChain<RuntimeException>())
        assertTrue(exception.hasInCauseChain<IllegalArgumentException>())
        assertTrue(exception.hasInCauseChain<IllegalStateException>())
        assertTrue(exception.hasInCauseChain<RuntimeException>(maxDepth = 1))
        assertTrue(exception.hasInCauseChain<IllegalArgumentException>(maxDepth = 1))
        assertFalse(exception.hasInCauseChain<IllegalStateException>(maxDepth = 1))
        assertTrue(exception.hasInCauseChain<RuntimeException>(maxDepth = 0))
        assertFalse(exception.hasInCauseChain<IllegalArgumentException>(maxDepth = 0))
        assertFalse(exception.hasInCauseChain<IllegalStateException>(maxDepth = 0))
    }
}
