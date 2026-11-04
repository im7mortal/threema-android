package ch.threema.app.threemasafe.usecases

import ch.threema.app.compose.preview.PreviewData.INSTANT_1
import ch.threema.app.threemasafe.ThreemaSafeViewState
import ch.threema.common.bytes
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class GetLastThreemaSafeBackupDataUseCaseTest {
    @Test
    fun `get successful state`() = runTest {
        val useCase = GetLastThreemaSafeBackupDataUseCase(
            preferenceService = mockk {
                every { getThreemaSafeBackupTimestamp() } returns INSTANT_1
                every { getThreemaSafeBackupSize() } returns 123
                every { getThreemaSafeErrorCode() } returns 0
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        assertEquals(
            ThreemaSafeViewState.LastBackupData(
                time = INSTANT_1,
                size = 123.bytes,
                status = ThreemaSafeViewState.LastBackupStatus.SUCCESS,
            ),
            useCase.call(),
        )
    }

    @Test
    fun `get connection error state`() = runTest {
        val useCase = GetLastThreemaSafeBackupDataUseCase(
            preferenceService = mockk {
                every { getThreemaSafeBackupTimestamp() } returns null
                every { getThreemaSafeBackupSize() } returns 0
                every { getThreemaSafeErrorCode() } returns 1
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        assertEquals(
            ThreemaSafeViewState.LastBackupData(
                time = null,
                size = null,
                status = ThreemaSafeViewState.LastBackupStatus.CONNECTION_ERROR,
            ),
            useCase.call(),
        )
    }

    @Test
    fun `get upload failed state`() = runTest {
        val useCase = GetLastThreemaSafeBackupDataUseCase(
            preferenceService = mockk {
                every { getThreemaSafeBackupTimestamp() } returns null
                every { getThreemaSafeBackupSize() } returns 0
                every { getThreemaSafeErrorCode() } returns 4
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        assertEquals(
            ThreemaSafeViewState.LastBackupData(
                time = null,
                size = null,
                status = ThreemaSafeViewState.LastBackupStatus.UPLOAD_FAILED,
            ),
            useCase.call(),
        )
    }

    @Test
    fun `get size limit exceeded state`() = runTest {
        val useCase = GetLastThreemaSafeBackupDataUseCase(
            preferenceService = mockk {
                every { getThreemaSafeBackupTimestamp() } returns null
                every { getThreemaSafeBackupSize() } returns 0
                every { getThreemaSafeErrorCode() } returns 6
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        assertEquals(
            ThreemaSafeViewState.LastBackupData(
                time = null,
                size = null,
                status = ThreemaSafeViewState.LastBackupStatus.SIZE_LIMIT_EXCEEDED,
            ),
            useCase.call(),
        )
    }

    @Test
    fun `get generic error state`() = runTest {
        val useCase = GetLastThreemaSafeBackupDataUseCase(
            preferenceService = mockk {
                every { getThreemaSafeBackupTimestamp() } returns null
                every { getThreemaSafeBackupSize() } returns 0
                every { getThreemaSafeErrorCode() } returns 2
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        assertEquals(
            ThreemaSafeViewState.LastBackupData(
                time = null,
                size = null,
                status = ThreemaSafeViewState.LastBackupStatus.GENERIC_ERROR,
            ),
            useCase.call(),
        )
    }
}
