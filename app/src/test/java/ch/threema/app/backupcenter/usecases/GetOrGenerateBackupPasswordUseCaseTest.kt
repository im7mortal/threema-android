package ch.threema.app.backupcenter.usecases

import ch.threema.app.backupcenter.models.LocalDataBackupPassword
import ch.threema.app.preference.service.PreferenceService
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class GetOrGenerateBackupPasswordUseCaseTest {

    @Test
    fun `existing password is loaded`() = runTest {
        val passwordMock = mockk<LocalDataBackupPassword>()
        val useCase = GetOrGenerateBackupPasswordUseCase(
            preferenceService = mockk {
                every { getLocalDataBackupPassword() } returns passwordMock
            },
            secureRandom = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )
        val wrapper = useCase.call()
        assertEquals(
            GetOrGenerateBackupPasswordUseCase.PasswordWrapper(
                password = passwordMock,
                newlyGenerated = false,
            ),
            wrapper,
        )
    }

    @Test
    fun `new password is generated`() = runTest {
        val useCase = GetOrGenerateBackupPasswordUseCase(
            preferenceService = mockk {
                every { getLocalDataBackupPassword() } returns null
                every { setLocalDataBackupPassword(null) } just runs
            },
            secureRandom = createSecureRandomMock(),
            dispatcherProvider = testDispatcherProvider(),
        )
        val wrapper = useCase.call()
        assertEquals(
            GetOrGenerateBackupPasswordUseCase.PasswordWrapper(
                password = LocalDataBackupPassword(PASSWORD_STRING),
                newlyGenerated = true,
            ),
            wrapper,
        )
    }

    @Test
    fun `new password is generated if forced, existing password is forgotten`() = runTest {
        val passwordMock = mockk<LocalDataBackupPassword>()
        val preferenceServiceMock = mockk<PreferenceService> {
            every { getLocalDataBackupPassword() } returns passwordMock
            every { setLocalDataBackupPassword(null) } just runs
        }
        val useCase = GetOrGenerateBackupPasswordUseCase(
            preferenceService = preferenceServiceMock,
            secureRandom = createSecureRandomMock(),
            dispatcherProvider = testDispatcherProvider(),
        )
        val wrapper = useCase.call(forceNew = true)
        assertEquals(
            GetOrGenerateBackupPasswordUseCase.PasswordWrapper(
                password = LocalDataBackupPassword(PASSWORD_STRING),
                newlyGenerated = true,
            ),
            wrapper,
        )
        verify(exactly = 1) { preferenceServiceMock.setLocalDataBackupPassword(null) }
    }

    companion object {
        private const val PASSWORD_STRING = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFGHIJKLMNOPQR"

        private fun createSecureRandomMock() = mockk<SecureRandom> {
            var i = 0
            every { nextInt(36) } answers { (i++) % 36 }
        }
    }
}
