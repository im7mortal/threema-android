package ch.threema.app.backuprestore.libthreema

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import ch.threema.app.services.FileService
import ch.threema.common.DispatcherProvider
import ch.threema.common.bytes
import ch.threema.domain.stores.IdentityStore
import ch.threema.testhelpers.createTempDirectory
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import testdata.TestData

@OptIn(ExperimentalCoroutinesApi::class)
class BackupCreateJobTest {

    private val finalBackupDirPath = "finalBackupDirPath"

    private val backupWriterEventChannel = Channel<BackupWriter.BackupWriteEvent>()

    @BeforeTest
    fun setUp() {
        mockkConstructor(BackupWriter::class)
        every { anyConstructed<BackupWriter>().writeBackupFile() } returns backupWriterEventChannel.receiveAsFlow()
    }

    @AfterTest
    fun tearDown() {
        unmockkConstructor(BackupWriter::class)
    }

    @Test
    fun `temp file is written to final file`() = runTest {
        // Arrange
        val finalOutputStream = ByteArrayOutputStream()
        val expectedWrittenBytes = byteArrayOf(0, 1, 2, 3)
        val tempFile = File(createTempDirectory(), "tempBackupFile").apply {
            writeBytes(expectedWrittenBytes)
        }
        assertTrue(tempFile.exists())
        val creator = getCreator(
            finalOutputStream = finalOutputStream,
            tempFile = tempFile,
            dispatcherProvider = testDispatcherProvider(),
            backupCreateStateManager = mockk(relaxed = true), // we don't care about events in this test
        )
        val backupCreateParameters = BackupCreateParameters(
            backupUri = mockBackupUri(),
            backupPassword = "12345678",
            includeMedia = false,
        )

        // Act
        val backupCreateJob = creator.startBackupCreateJob(
            backupCreateParameters = backupCreateParameters,
        )

        backupWriterEventChannel.send(BackupWriter.BackupWriteEvent.Finished.Success("finalFileName"))
        backupWriterEventChannel.close()

        backupCreateJob.backupFinished.await()

        advanceUntilIdle()

        // Assert
        assertContentEquals(expectedWrittenBytes, finalOutputStream.toByteArray())
        assertFalse(tempFile.exists())
    }

    @Test
    fun `deferred is completed with failure`() = runTest {
        // Arrange
        val finalOutputStream: OutputStream = mockk()
        val tempFile = File(createTempDirectory(), "tempBackupFile").apply {
            createNewFile()
        }
        assertTrue(tempFile.exists())
        val creator = getCreator(
            finalOutputStream = finalOutputStream,
            tempFile = tempFile,
            dispatcherProvider = testDispatcherProvider(),
            backupCreateStateManager = mockk(relaxed = true), // we don't care about events in this test
        )
        val backupCreateParameters = BackupCreateParameters(
            backupUri = mockBackupUri(),
            backupPassword = "12345678",
            includeMedia = false,
        )

        // Act
        val backupCreateJob = creator.startBackupCreateJob(
            backupCreateParameters = backupCreateParameters,
        )

        backupWriterEventChannel.send(BackupWriter.BackupWriteEvent.Finished.Failure.UnexpectedError(IllegalStateException()))
        backupWriterEventChannel.close()

        advanceUntilIdle()

        // Assert
        assertIs<BackupCreateResult.Failure>(backupCreateJob.backupFinished.await())
        assertFalse(tempFile.exists())
    }

    @Test
    fun `job is canceled if requested`() = runTest {
        // Arrange
        val finalOutputStream: OutputStream = mockk()
        val tempFile = File(createTempDirectory(), "tempBackupFile").apply {
            createNewFile()
        }
        assertTrue(tempFile.exists())
        val creator = getCreator(
            finalOutputStream = finalOutputStream,
            tempFile = tempFile,
            dispatcherProvider = testDispatcherProvider(),
            backupCreateStateManager = mockk(relaxed = true), // we don't care about events in this test
        )
        val backupCreateParameters = BackupCreateParameters(
            backupUri = mockBackupUri(),
            backupPassword = "12345678",
            includeMedia = false,
        )

        // Act
        val backupCreateJob = creator.startBackupCreateJob(
            backupCreateParameters = backupCreateParameters,
        )

        backupCreateJob.cancelAndJoin()

        // Assert
        assertFailsWith<CancellationException> {
            backupCreateJob.backupFinished.await()
        }
        assertFalse(tempFile.exists())
    }

    @Test
    fun `events are emitted to progress manager`() = runTest {
        // Arrange
        val backupCreateStateFlow = MutableStateFlow<BackupCreateState>(BackupCreateState.Initial)
        val backupCreateStateManager: BackupCreateStateManager = mockk {
            every { backupCreateState } returns backupCreateStateFlow
            every { updateState(any()) } answers { backupCreateStateFlow.value = firstArg() }
        }
        val tempFile = File(createTempDirectory(), "tempBackupFile").apply {
            appendText("12345")
        }
        assertTrue(tempFile.exists())
        val creator = getCreator(
            finalOutputStream = ByteArrayOutputStream(),
            tempFile = tempFile,
            dispatcherProvider = testDispatcherProvider(),
            backupCreateStateManager = backupCreateStateManager,
        )
        val backupCreateParameters = BackupCreateParameters(
            backupUri = mockBackupUri(),
            backupPassword = "12345678",
            includeMedia = false,
        )

        // Act
        val backupCreateJob = creator.startBackupCreateJob(
            backupCreateParameters = backupCreateParameters,
        )

        backupCreateStateFlow.test {
            expectItem(BackupCreateState.Initial)

            backupWriterEventChannel.send(BackupWriter.BackupWriteEvent.InProgress(backupStep = BackupStep.Contacts))
            expectItem(BackupCreateState.InProgress(backupStep = BackupStep.Contacts, includeMedia = false))

            backupWriterEventChannel.send(BackupWriter.BackupWriteEvent.Finished.Success("finalFileName"))
            awaitItem().let { item ->
                assertIs<BackupCreateState.Done.Success>(item)
                assertEquals(5.bytes, item.fileSize)
                assertEquals("finalBackupDirPath", item.fileLocation)
                assertFalse(item.includeMedia)
            }

            backupWriterEventChannel.close()
            ensureAllEventsConsumed()
        }

        // Assert
        assertIs<BackupCreateResult.Success>(backupCreateJob.backupFinished.await())
    }

    private fun mockBackupUri(): Uri = mockk {
        every { scheme } returns ContentResolver.SCHEME_FILE
        every { path } returns finalBackupDirPath
    }

    private fun getCreator(
        finalOutputStream: OutputStream,
        tempFile: File,
        dispatcherProvider: DispatcherProvider,
        backupCreateStateManager: BackupCreateStateManager,
    ) = BackupCreateJob.Creator(
        appContext = mockAppContext(finalOutputStream),
        identityStore = mockIdentityStore(),
        fileService = mockFileService(tempFile),
        dispatcherProvider = dispatcherProvider,
        userProfileProvider = mockk(),
        settingsProvider = mockk(),
        cspNonceProvider = mockk(),
        contactProvider = mockk(),
        groupProvider = mockk(),
        distributionListProvider = mockk(),
        pollProvider = mockk(),
        conversationProvider = mockk(),
        backupCreateStateManager = backupCreateStateManager,
    )

    private fun mockAppContext(outputStream: OutputStream): Context = mockk {
        every { contentResolver } returns mockk {
            every { openOutputStream(any()) } returns outputStream
        }
    }

    private fun mockFileService(tempBackupFile: File): FileService = mockk {
        every { createTempFile(any(), any()) } returns tempBackupFile
    }

    private fun mockIdentityStore(): IdentityStore = mockk {
        every { getIdentityString() } returns TestData.Identities.ME.value
        every { getPrivateKey() } returns TestData.testPrivateKey
        every { getServerGroup() } returns "1"
    }
}
