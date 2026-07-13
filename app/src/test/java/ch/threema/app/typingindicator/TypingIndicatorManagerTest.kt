package ch.threema.app.typingindicator

import app.cash.turbine.test
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import testdata.TestData.Identities

@OptIn(ExperimentalCoroutinesApi::class)
class TypingIndicatorManagerTest {

    @Test
    fun `set and query typing indicators`() = runTest {
        val typingIndicatorManager = TypingIndicatorManager(
            dispatcherProvider = testDispatcherProvider(),
            timeSource = testTimeSource,
        )

        assertFalse(typingIndicatorManager.isTyping(Identities.OTHER_1))
        assertFalse(typingIndicatorManager.isTyping(Identities.OTHER_2))

        typingIndicatorManager.setIsTyping(Identities.OTHER_1, false)
        assertFalse(typingIndicatorManager.isTyping(Identities.OTHER_1))
        assertFalse(typingIndicatorManager.isTyping(Identities.OTHER_2))

        typingIndicatorManager.setIsTyping(Identities.OTHER_1, true)
        assertTrue(typingIndicatorManager.isTyping(Identities.OTHER_1))
        assertFalse(typingIndicatorManager.isTyping(Identities.OTHER_2))

        typingIndicatorManager.setIsTyping(Identities.OTHER_2, true)
        assertTrue(typingIndicatorManager.isTyping(Identities.OTHER_1))
        assertTrue(typingIndicatorManager.isTyping(Identities.OTHER_2))

        typingIndicatorManager.setIsTyping(Identities.OTHER_1, false)
        assertFalse(typingIndicatorManager.isTyping(Identities.OTHER_1))
        assertTrue(typingIndicatorManager.isTyping(Identities.OTHER_2))
    }

    @Test
    fun `watch typing indicators`() = runTest {
        val typingIndicatorManager = TypingIndicatorManager(
            dispatcherProvider = testDispatcherProvider(),
            timeSource = testTimeSource,
        )

        typingIndicatorManager.watchTypingIdentities().test {
            expectItem(emptySet())

            typingIndicatorManager.setIsTyping(Identities.OTHER_1, isTyping = false)
            expectNoEvents()

            typingIndicatorManager.setIsTyping(Identities.OTHER_1, isTyping = true)
            expectItem(setOf(Identities.OTHER_1))

            typingIndicatorManager.setIsTyping(Identities.OTHER_1, isTyping = true)
            expectNoEvents()

            typingIndicatorManager.setIsTyping(Identities.OTHER_1, isTyping = false)
            expectItem(emptySet())

            typingIndicatorManager.setIsTyping(Identities.OTHER_2, isTyping = true)
            expectItem(setOf(Identities.OTHER_2))

            typingIndicatorManager.setIsTyping(Identities.OTHER_1, isTyping = true)
            expectItem(setOf(Identities.OTHER_1, Identities.OTHER_2))

            typingIndicatorManager.setIsTyping(Identities.OTHER_2, isTyping = false)
            expectItem(setOf(Identities.OTHER_1))
        }
    }

    @Test
    fun `typing indicators are reset after a timeout`() = runTest {
        val typingIndicatorManager = TypingIndicatorManager(
            dispatcherProvider = testDispatcherProvider(),
            timeSource = testTimeSource,
        )

        typingIndicatorManager.watchTypingIdentities().test {
            expectItem(emptySet())

            typingIndicatorManager.setIsTyping(Identities.OTHER_1, isTyping = true)
            expectItem(setOf(Identities.OTHER_1))

            delay(12.seconds)
            expectNoEvents()
            assertTrue(typingIndicatorManager.isTyping(Identities.OTHER_1))

            // After 15+ seconds, the typing state is automatically reset
            delay(4.seconds)
            expectItem(emptySet())
            assertFalse(typingIndicatorManager.isTyping(Identities.OTHER_1))

            typingIndicatorManager.setIsTyping(Identities.OTHER_1, isTyping = true)
            skipItems(1)
            delay(12.seconds)

            // The reset is canceled if typing is reported again before the timeout is reached
            typingIndicatorManager.setIsTyping(Identities.OTHER_1, isTyping = true)
            delay(4.seconds)
            expectNoEvents()
            assertTrue(typingIndicatorManager.isTyping(Identities.OTHER_1))

            // Another user typing does not interfere with the resetting
            typingIndicatorManager.setIsTyping(Identities.OTHER_2, isTyping = true)
            skipItems(1)
            delay(12.seconds)
            expectItem(setOf(Identities.OTHER_2))
            assertFalse(typingIndicatorManager.isTyping(Identities.OTHER_1))
            assertTrue(typingIndicatorManager.isTyping(Identities.OTHER_2))

            delay(4.seconds)
            expectItem(emptySet())
            assertFalse(typingIndicatorManager.isTyping(Identities.OTHER_2))
        }
    }
}
