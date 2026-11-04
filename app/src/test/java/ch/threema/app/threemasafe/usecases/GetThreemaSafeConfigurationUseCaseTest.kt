package ch.threema.app.threemasafe.usecases

import ch.threema.app.threemasafe.ThreemaSafeViewState
import ch.threema.common.bytes
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class GetThreemaSafeConfigurationUseCaseTest {
    @Test
    fun `default server config`() = runTest {
        val useCase = GetThreemaSafeConfigurationUseCase(
            preferenceService = mockk {
                every { getThreemaSafeServerInfo() } returns mockk {
                    every { isDefaultServer } returns true
                }
                every { getThreemaSafeServerMaxUploadSize() } returns 123
                every { getThreemaSafeServerRetention() } returns 180
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        assertEquals(
            ThreemaSafeViewState.Configuration(
                server = null,
                maxBackupSize = 123.bytes,
                backupRetentionDays = 180,
            ),
            useCase.call(),
        )
    }

    @Test
    fun `custom server config`() = runTest {
        val useCase = GetThreemaSafeConfigurationUseCase(
            preferenceService = mockk {
                every { getThreemaSafeServerInfo() } returns mockk {
                    every { isDefaultServer } returns false
                    every { hostName } returns "example.com"
                }
                every { getThreemaSafeServerMaxUploadSize() } returns 123
                every { getThreemaSafeServerRetention() } returns 180
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        assertEquals(
            ThreemaSafeViewState.Configuration(
                server = "example.com",
                maxBackupSize = 123.bytes,
                backupRetentionDays = 180,
            ),
            useCase.call(),
        )
    }
}
