package ch.threema.app.usecases

import ch.threema.app.BuildConfig
import ch.threema.testhelpers.createTempDirectory
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue

class ExportDebugLogUseCaseTest {

    private lateinit var shareDirectory: File
    private lateinit var tombstoneDirectory: File

    @BeforeTest
    fun setUp() {
        shareDirectory = createTempDirectory()
        tombstoneDirectory = createTempDirectory()
    }

    @AfterTest
    fun tearDown() {
        shareDirectory.deleteRecursively()
        tombstoneDirectory.deleteRecursively()
    }

    @Test
    fun `create zip file from debug log files`() = runTest {
        val logFile1 = File.createTempFile("logfile", "1")
        val logFile2 = File.createTempFile("logfile", "2")
        logFile1.writeText("Hello\nWorld\n")
        logFile2.writeText("This is a test\n")

        val useCase = ExportDebugLogUseCase(
            getDebugMetaDataUseCase = mockk {
                every { call() } returns "my meta data"
            },
            debugLogFileManager = mockk {
                every { getLogFiles() } returns listOf(logFile1, logFile2)
            },
            appDirectoryProvider = mockk {
                every { this@mockk.shareDirectory } returns this@ExportDebugLogUseCaseTest.shareDirectory
                every { this@mockk.tombstonesDirectory } returns this@ExportDebugLogUseCaseTest.tombstoneDirectory
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        val exportedFile = useCase.call()

        val zipFile = ZipFile(exportedFile)
        assertEquals(
            setOf("debug_log.txt", "meta_data.txt"),
            zipFile.getFileNames(),
        )
        assertEquals(
            "Hello\nWorld\nThis is a test\n",
            zipFile.getFileContents("debug_log.txt"),
        )
        assertEquals(
            "my meta data",
            zipFile.getFileContents("meta_data.txt"),
        )
    }

    @Test
    fun `create zip file from fallback debug log files`() = runTest {
        val logFile1 = File.createTempFile("logfile", "1")
        val logFile2 = File.createTempFile("logfile", "2")
        logFile1.writeText("Hello\nWorld\n")
        logFile2.writeText("This is a test\n")

        val useCase = ExportDebugLogUseCase(
            getDebugMetaDataUseCase = mockk {
                every { call() } returns "my meta data"
            },
            debugLogFileManager = mockk {
                every { getLogFiles() } returns emptyList()
                every { getFallbackLogFiles() } returns listOf(logFile1, logFile2)
            },
            appDirectoryProvider = mockk {
                every { this@mockk.shareDirectory } returns this@ExportDebugLogUseCaseTest.shareDirectory
                every { this@mockk.tombstonesDirectory } returns this@ExportDebugLogUseCaseTest.tombstoneDirectory
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        val exportedFile = useCase.call()

        val zipFile = ZipFile(exportedFile)
        assertEquals(
            setOf("debug_log.txt", "meta_data.txt"),
            zipFile.getFileNames(),
        )
        assertEquals(
            "Hello\nWorld\nThis is a test\n",
            zipFile.getFileContents("debug_log.txt"),
        )
        assertEquals(
            "my meta data",
            zipFile.getFileContents("meta_data.txt"),
        )
    }

    @Test
    fun `created zip file includes tombstones`() = runTest {
        assumeTrue(BuildConfig.LOG_TOMBSTONES_FOR_NATIVE_CRASHES)

        val logFile = File.createTempFile("logfile", "1")
        logFile.writeText("Hello\nWorld\n")
        val tombstoneFile1 = File(tombstoneDirectory, "tombstone1")
        val tombstoneFile2 = File(tombstoneDirectory, "tombstone2")
        tombstoneFile1.writeText("Tombstone 1")
        tombstoneFile2.writeText("Tombstone 2")

        val useCase = ExportDebugLogUseCase(
            getDebugMetaDataUseCase = mockk {
                every { call() } returns "my meta data"
            },
            debugLogFileManager = mockk {
                every { getLogFiles() } returns listOf(logFile)
            },
            appDirectoryProvider = mockk {
                every { this@mockk.shareDirectory } returns this@ExportDebugLogUseCaseTest.shareDirectory
                every { this@mockk.tombstonesDirectory } returns this@ExportDebugLogUseCaseTest.tombstoneDirectory
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        val exportedFile = useCase.call()

        val zipFile = ZipFile(exportedFile)
        assertEquals(
            setOf("debug_log.txt", "meta_data.txt", "tombstone1", "tombstone2"),
            zipFile.getFileNames(),
        )
        assertEquals(
            "Tombstone 1",
            zipFile.getFileContents("tombstone1"),
        )
        assertEquals(
            "Tombstone 2",
            zipFile.getFileContents("tombstone2"),
        )
    }

    @Test
    fun `previous file is deleted`() = runTest {
        val expectedFile = File(shareDirectory, "debug_log.zip")
        val logFile = File.createTempFile("logfile", "test")

        val useCase = ExportDebugLogUseCase(
            getDebugMetaDataUseCase = mockk {
                every { call() } returns "my meta data"
            },
            debugLogFileManager = mockk {
                every { getLogFiles() } returns listOf(logFile)
            },
            appDirectoryProvider = mockk {
                every { this@mockk.shareDirectory } returns this@ExportDebugLogUseCaseTest.shareDirectory
                every { this@mockk.tombstonesDirectory } returns this@ExportDebugLogUseCaseTest.tombstoneDirectory
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        expectedFile.createNewFile()

        val exportedFile = useCase.call()
        assertEquals(expectedFile, exportedFile)

        val zipFile = ZipFile(exportedFile)
        assertEquals(
            setOf("debug_log.txt", "meta_data.txt"),
            zipFile.getFileNames(),
        )
    }

    private fun ZipFile.getFileNames(): Set<String> =
        entries().toList().map { entry -> entry.name }.toSet()

    private fun ZipFile.getFileContents(fileName: String): String =
        getInputStream(getEntry(fileName)).use { inputStream ->
            inputStream.reader().readText()
        }
}
