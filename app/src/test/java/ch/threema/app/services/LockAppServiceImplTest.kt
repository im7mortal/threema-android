package ch.threema.app.services

import app.cash.turbine.test
import ch.threema.app.preference.service.PreferenceService
import ch.threema.testhelpers.expectItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest
import testdata.TestData

class LockAppServiceImplTest {

    @Test
    fun `initial locked state reflects preferences`() {
        LockAppServiceImpl(
            lockAppTimer = mockk(),
            preferencesService = mockk {
                every { isAppLockEnabled() } returns false
            },
            identityProvider = mockk { every { getIdentity() } returns TestData.Identities.ME },
        )
            .let { lockAppService ->
                assertFalse(lockAppService.isLocked)
                assertEquals(LockAppService.LockState.UNLOCKED, lockAppService.watchLockState().value)
            }

        LockAppServiceImpl(
            lockAppTimer = mockk(),
            preferencesService = mockk {
                every { isAppLockEnabled() } returns true
            },
            identityProvider = mockk { every { getIdentity() } returns TestData.Identities.ME },
        )
            .let { lockAppService ->
                assertTrue(lockAppService.isLocked)
                assertEquals(LockAppService.LockState.LOCKED, lockAppService.watchLockState().value)
            }
    }

    @Test
    fun `locking is enabled only when app lock is enabled and an identity is known`() {
        LockAppServiceImpl(
            lockAppTimer = mockk(),
            preferencesService = mockk { every { isAppLockEnabled() } returns false },
            identityProvider = mockk { every { getIdentity() } returns null },
        )
            .let { lockAppService ->
                assertFalse(lockAppService.isLockingEnabled)
            }

        LockAppServiceImpl(
            lockAppTimer = mockk(),
            preferencesService = mockk { every { isAppLockEnabled() } returns true },
            identityProvider = mockk { every { getIdentity() } returns null },
        )
            .let { lockAppService ->
                assertFalse(lockAppService.isLockingEnabled)
            }

        LockAppServiceImpl(
            lockAppTimer = mockk(),
            preferencesService = mockk { every { isAppLockEnabled() } returns false },
            identityProvider = mockk { every { getIdentity() } returns TestData.Identities.ME },
        )
            .let { lockAppService ->
                assertFalse(lockAppService.isLockingEnabled)
            }

        LockAppServiceImpl(
            lockAppTimer = mockk(),
            preferencesService = mockk { every { isAppLockEnabled() } returns true },
            identityProvider = mockk { every { getIdentity() } returns TestData.Identities.ME },
        )
            .let { lockAppService ->
                assertTrue(lockAppService.isLockingEnabled)
            }
    }

    @Test
    fun `lock flow`() = runTest {
        val correctPin = "1234"
        val gracePeriod = 10.seconds
        val lockAppAlarmSchedulerMock = mockk<LockAppAlarmScheduler>(relaxed = true)
        val timeSource = TestTimeSource()
        var lockMechanism = PreferenceService.LockMechanism.PIN
        val lockAppService = LockAppServiceImpl(
            lockAppTimer = LockAppTimer(
                preferencesService = mockk {
                    every { getAppLockGraceTime() } returns gracePeriod
                },
                timeSource = timeSource,
                lockAppAlarmScheduler = lockAppAlarmSchedulerMock,
            ),
            preferencesService = mockk {
                every { isAppLockEnabled() } returns true
                every { getLockMechanism() } answers { lockMechanism }
                every { isPinCodeCorrect(any()) } answers { firstArg<String>() == correctPin }
            },
            identityProvider = mockk { every { getIdentity() } returns TestData.Identities.ME },
        )

        lockAppService.watchLockState().test {
            // App is initially locked
            expectItem(LockAppService.LockState.LOCKED)

            // Calling lock() while already locked does nothing
            lockAppService.lock()
            expectNoEvents()

            // Entering the wrong PIN does not unlock the app
            assertFalse(lockAppService.unlock("9999"))
            expectNoEvents()

            // Entering the correct PIN unlocks the app
            assertTrue(lockAppService.unlock(correctPin))
            expectItem(LockAppService.LockState.UNLOCKED)
            assertFalse(lockAppService.isLocked)
            verify(exactly = 1) { lockAppAlarmSchedulerMock.cancelTimer() }

            // If there was a timer, it would have expired now, but since we never started it, nothing happens here
            timeSource += 12.seconds
            lockAppService.lockIfExpired()
            expectNoEvents()

            // Start timer now
            lockAppService.onUserActivity()
            verify(exactly = 1) { lockAppAlarmSchedulerMock.startTimer(delay = gracePeriod) }

            // Timer has not yet expired, nothing happens
            timeSource += 3.seconds
            lockAppService.lockIfExpired()
            expectNoEvents()

            // Timer has now expired, app gets locked
            timeSource += 8.seconds
            lockAppService.lockIfExpired()
            expectItem(LockAppService.LockState.LOCKED)
            assertTrue(lockAppService.isLocked)
            verify(exactly = 2) { lockAppAlarmSchedulerMock.cancelTimer() }

            // Change lock mechanism and unlock again
            lockMechanism = PreferenceService.LockMechanism.BIOMETRIC
            lockAppService.unlock(pin = null)
            expectItem(LockAppService.LockState.UNLOCKED)
            assertFalse(lockAppService.isLocked)
            verify(exactly = 3) { lockAppAlarmSchedulerMock.cancelTimer() }

            // Timer would expire, but it gets restarted
            timeSource += 12.seconds
            lockAppService.onUserActivity()
            verify(exactly = 2) { lockAppAlarmSchedulerMock.startTimer(delay = gracePeriod) }
            lockAppService.lockIfExpired()
            expectNoEvents()

            // Lock app explicitly, regardless of timer
            lockAppService.lock()
            expectItem(LockAppService.LockState.LOCKED)
            assertTrue(lockAppService.isLocked)
            verify(exactly = 4) { lockAppAlarmSchedulerMock.cancelTimer() }
        }
    }
}
