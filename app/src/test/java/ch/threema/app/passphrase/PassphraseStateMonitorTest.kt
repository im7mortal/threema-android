package ch.threema.app.passphrase

import ch.threema.app.services.PassphraseService
import ch.threema.localcrypto.models.PassphraseLockState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PassphraseStateMonitorTest {

    @Test
    fun `passphrase service is started and stopped when needed`() = runTest {
        val passphraseServiceStarterMock = mockk<PassphraseService.Starter>(relaxed = true)
        val passphraseLockStateFlow = MutableStateFlow(PassphraseLockState.NO_PASSPHRASE)
        val passphraseStateMonitor = PassphraseStateMonitor(
            masterKeyManager = mockk {
                every { passphraseLockState } returns passphraseLockStateFlow
            },
            passphraseServiceStarter = passphraseServiceStarterMock,
        )

        val job = launch {
            passphraseStateMonitor.run()
        }

        // Initially our lock is NO_PASSPHRASE, but initially the service also isn't running, so we don't need to do anything
        advanceUntilIdle()
        verify(exactly = 0) { passphraseServiceStarterMock.stop() }
        verify(exactly = 0) { passphraseServiceStarterMock.startIfNeeded() }

        // When the passphrase state changes to LOCKED, we expect the service to remain stopped
        passphraseLockStateFlow.value = PassphraseLockState.LOCKED
        advanceUntilIdle()
        verify(exactly = 0) { passphraseServiceStarterMock.stop() }
        verify(exactly = 0) { passphraseServiceStarterMock.startIfNeeded() }

        // When the passphrase state changes to UNLOCKED, we expect the service to be started
        passphraseLockStateFlow.value = PassphraseLockState.UNLOCKED
        advanceUntilIdle()
        verify(exactly = 0) { passphraseServiceStarterMock.stop() }
        verify(exactly = 1) { passphraseServiceStarterMock.startIfNeeded() }

        // When the passphrase state changes to NO_PASSPHRASE, we expect the service to be stopped
        passphraseLockStateFlow.value = PassphraseLockState.NO_PASSPHRASE
        advanceUntilIdle()
        verify(exactly = 1) { passphraseServiceStarterMock.stop() }
        verify(exactly = 1) { passphraseServiceStarterMock.startIfNeeded() }

        job.cancel()
    }
}
