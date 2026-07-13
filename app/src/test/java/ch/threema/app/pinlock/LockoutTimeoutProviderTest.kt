package ch.threema.app.pinlock

import ch.threema.app.preference.service.PreferenceService
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LockoutTimeoutProviderTest {
    private val expectedTimeouts = listOf(
        30.seconds,
        1.minutes,
        2.minutes,
        5.minutes,
        15.minutes,
        30.minutes,
        1.hours,
    )

    @Test
    fun `timeout is increased`() {
        val lockoutTimeoutProvider = LockoutTimeoutProvider(
            preferenceService = getPreferenceServiceMock(),
        )

        assertEquals(expectedTimeouts[0], lockoutTimeoutProvider.getCurrentLockoutTimeout())
        lockoutTimeoutProvider.increaseTimeout()
        assertEquals(expectedTimeouts[1], lockoutTimeoutProvider.getCurrentLockoutTimeout())
    }

    @Test
    fun `timeout stays at maximum`() {
        val lockoutTimeoutProvider = LockoutTimeoutProvider(
            preferenceService = getPreferenceServiceMock(),
        )

        val amountOfIncreases = 10

        val expectedTimeouts = (0 until amountOfIncreases).map { index ->
            expectedTimeouts.getOrNull(index)
                ?: expectedTimeouts.last()
        }

        val actualTimeouts = buildList {
            repeat(amountOfIncreases) {
                add(lockoutTimeoutProvider.getCurrentLockoutTimeout())
                lockoutTimeoutProvider.increaseTimeout()
            }
        }

        assertContentEquals(expectedTimeouts, actualTimeouts)
    }

    @Test
    fun `timeout can be reset`() {
        val lockoutTimeoutProvider = LockoutTimeoutProvider(
            preferenceService = getPreferenceServiceMock(),
        )

        assertEquals(expectedTimeouts[0], lockoutTimeoutProvider.getCurrentLockoutTimeout())
        lockoutTimeoutProvider.increaseTimeout()
        assertEquals(expectedTimeouts[1], lockoutTimeoutProvider.getCurrentLockoutTimeout())
        lockoutTimeoutProvider.resetTimeout()
        assertEquals(expectedTimeouts[0], lockoutTimeoutProvider.getCurrentLockoutTimeout())
    }

    @Test
    fun `correct timeout is returned if it already starts at a value greater than 0`() {
        val lockoutTimeoutProvider = LockoutTimeoutProvider(
            preferenceService = getPreferenceServiceMock(
                initialLockoutTimeoutIndex = 3,
            ),
        )

        assertEquals(expectedTimeouts[3], lockoutTimeoutProvider.getCurrentLockoutTimeout())
        lockoutTimeoutProvider.increaseTimeout()
        assertEquals(expectedTimeouts[4], lockoutTimeoutProvider.getCurrentLockoutTimeout())
        lockoutTimeoutProvider.resetTimeout()
        assertEquals(expectedTimeouts[0], lockoutTimeoutProvider.getCurrentLockoutTimeout())
    }

    private fun getPreferenceServiceMock(initialLockoutTimeoutIndex: Int = 0): PreferenceService = mockk {
        var lockoutTimeoutIndex = initialLockoutTimeoutIndex
        every { getLockoutTimeoutIndex() } answers { lockoutTimeoutIndex }
        every { setLockoutTimeoutIndex(any()) } answers {
            lockoutTimeoutIndex = firstArg()
        }
    }
}
