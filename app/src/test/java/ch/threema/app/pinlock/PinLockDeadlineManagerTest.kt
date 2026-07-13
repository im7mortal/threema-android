package ch.threema.app.pinlock

import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.plus
import ch.threema.testhelpers.TestTimeProvider
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class PinLockDeadlineManagerTest {

    @Test
    fun `reaching max attempts increases deadline`() {
        val testTimeProvider = TestTimeProvider(
            initialTimestamp = 0,
        )
        val pinLockDeadlineManager = PinLockDeadlineManager(
            timeProvider = testTimeProvider,
            preferenceService = getPreferenceServiceMock(),
            lockoutTimeoutProvider = getLinearLockoutTimeProviderMock(),
        )

        assertNull(pinLockDeadlineManager.lockoutDeadline)
        pinLockDeadlineManager.onMaxAttemptsReached()
        assertEquals(testTimeProvider.get() + 1.seconds, pinLockDeadlineManager.lockoutDeadline)
    }

    private fun getLinearLockoutTimeProviderMock(): LockoutTimeoutProvider = mockk {
        var lockoutTimeout = 1
        every { getCurrentLockoutTimeout() } answers { lockoutTimeout.seconds }
        every { increaseTimeout() } answers {
            lockoutTimeout++
        }
        every { resetTimeout() } answers {
            lockoutTimeout = 1
        }
    }

    private fun getPreferenceServiceMock(initialLockoutDeadline: Instant? = null): PreferenceService = mockk {
        var lockoutDeadline: Instant? = initialLockoutDeadline
        every { getLockoutDeadline() } answers { lockoutDeadline }
        every { setLockoutDeadline(any()) } answers {
            lockoutDeadline = firstArg<Instant?>()
        }
    }
}
