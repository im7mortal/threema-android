package ch.threema.app.logging

import ch.threema.app.BuildConfig
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import ch.threema.logging.backend.DebugLogFileManager
import ch.threema.testhelpers.TestTimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.test.AutoCloseKoinTest
import org.koin.test.mock.declare

class DebugLogFileCleanupWorkerTest : AutoCloseKoinTest() {

    @BeforeTest
    fun setUp() {
        startKoin { }
    }

    @Test
    fun `log files older than 10 days are deleted, newer ones are kept`() = runTest {
        val oldLogFile1 = mockFile(age = 13.days)
        val oldLogFile2 = mockFile(age = 12.days)
        val oldLogFile3 = mockFile(age = 11.days)
        val oldFallbackLogFile1 = mockFile(age = 13.days)
        val oldFallbackLogFile2 = mockFile(age = 12.days)
        val oldFallbackLogFile3 = mockFile(age = 11.days)
        val newLogFile1 = mockFile(age = 10.days)
        val newLogFile2 = mockFile(age = 9.days)
        val newFallbackLogFile1 = mockFile(age = 10.days)
        val newFallbackLogFile2 = mockFile(age = 9.days)
        val oldTombstoneFile1 = mockFile(age = 5.days)
        val newTombstoneFile1 = mockFile(age = 1.days)

        val debugLogFileManagerMock = mockk<DebugLogFileManager> {
            every { getLogFiles() } returns listOf(
                oldLogFile1,
                oldLogFile2,
                oldLogFile3,
                newLogFile1,
                newLogFile2,
            )
            every { getFallbackLogFiles() } returns listOf(
                oldFallbackLogFile1,
                oldFallbackLogFile2,
                oldFallbackLogFile3,
                newFallbackLogFile1,
                newFallbackLogFile2,
            )
        }
        val appDirectoryProviderMock = mockk<AppDirectoryProvider> {
            every { tombstonesDirectory } returns mockk {
                every { listFiles() } returns arrayOf(
                    oldTombstoneFile1,
                    newTombstoneFile1,
                )
            }
        }
        val testTimeProvider = TestTimeProvider(now)
        declare { debugLogFileManagerMock }
        declare<TimeProvider> { testTimeProvider }
        declare<AppDirectoryProvider> { appDirectoryProviderMock }
        val worker = DebugLogFileCleanupWorker(mockk(), mockk())

        worker.doWork()

        verify(exactly = 1) { oldLogFile1.delete() }
        verify(exactly = 1) { oldLogFile2.delete() }
        verify(exactly = 1) { oldLogFile3.delete() }
        verify(exactly = 1) { oldFallbackLogFile1.delete() }
        verify(exactly = 1) { oldFallbackLogFile2.delete() }
        verify(exactly = 1) { oldFallbackLogFile3.delete() }
        verify(exactly = 0) { newLogFile1.delete() }
        verify(exactly = 0) { newLogFile2.delete() }
        verify(exactly = 0) { newFallbackLogFile1.delete() }
        verify(exactly = 0) { newFallbackLogFile2.delete() }
        if (BuildConfig.LOG_TOMBSTONES_FOR_NATIVE_CRASHES) {
            verify(exactly = 1) { oldTombstoneFile1.delete() }
            verify(exactly = 0) { newTombstoneFile1.delete() }
        }
    }

    private fun mockFile(age: Duration): File {
        val fileName = "${age.inWholeDays}d"
        return mockk(fileName) {
            every { name } returns fileName
            every { lastModified() } returns (now - age).toEpochMilli()
            every { delete() } returns true
        }
    }

    companion object {
        private val now = Instant.ofEpochMilli(1_766_068_000_000L)
    }
}
