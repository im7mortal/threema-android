package ch.threema.app.backuprestore.libthreema

import app.cash.turbine.test
import ch.threema.libthreema.DataBackupRestoreException
import ch.threema.testhelpers.expectItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import java.util.concurrent.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest

class BackupRestoreJobTest {

    private val backupReaderEventChannel = Channel<BackupReader.BackupRestoreEvent>()

    @BeforeTest
    fun setUp() {
        mockkConstructor(BackupReader::class)
        every { anyConstructed<BackupReader>().readBackupFile(any()) } returns backupReaderEventChannel.receiveAsFlow()
    }

    @AfterTest
    fun tearDown() {
        unmockkConstructor(BackupReader::class)
    }

    @Test
    fun `job completes with success on success restore event`() = runTest {
        val creator = getJobCreator()

        val restoreJob = creator.startBackupRestoreJob(createBackupRestoreParameters())

        backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.Finished.Success)
        backupReaderEventChannel.close()

        val result = restoreJob.backupRestorationFinished.await()

        assertEquals(BackupRestoreResult.Success, result)
    }

    @Test
    fun `job completes with failure exception on cancel restore event`() = runTest {
        val creator = getJobCreator()

        val restoreJob = creator.startBackupRestoreJob(createBackupRestoreParameters())

        backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.Finished.Failure.Canceled(CancellationException("That was cancelled")))
        backupReaderEventChannel.close()

        val result = restoreJob.backupRestorationFinished.await()

        assert(result is BackupRestoreResult.Failure)
    }

    @Test
    fun `job completes with failure exception on decryption failed restore event`() = runTest {
        val creator = getJobCreator()

        val restoreJob = creator.startBackupRestoreJob(createBackupRestoreParameters())

        backupReaderEventChannel.send(
            BackupReader.BackupRestoreEvent.Finished.Failure.DecryptionFailed(DataBackupRestoreException.DecryptionFailed("Decryption has failed")),
        )
        backupReaderEventChannel.close()

        val result = restoreJob.backupRestorationFinished.await()

        assert(result is BackupRestoreResult.Failure)
    }

    @Test
    fun `job completes with failure exception on unexpected exception restore event`() = runTest {
        val creator = getJobCreator()

        val restoreJob = creator.startBackupRestoreJob(createBackupRestoreParameters())

        backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.Finished.Failure.UnexpectedException(RuntimeException("That was unexpected")))
        backupReaderEventChannel.close()

        val result = restoreJob.backupRestorationFinished.await()

        assert(result is BackupRestoreResult.Failure)
    }

    @Test
    fun `job completes exceptionally if no 'done' event was received after restore has finished`() = runTest {
        val creator = getJobCreator()

        val restoreJob = creator.startBackupRestoreJob(createBackupRestoreParameters())

        backupReaderEventChannel.close()

        assertFailsWith<IllegalStateException> {
            restoreJob.backupRestorationFinished.await()
        }
    }

    @Test
    fun `next steps are emitted to restore state manager`() = runTest {
        val backupRestoreStateFlow = MutableStateFlow<BackupRestoreState>(BackupRestoreState.Initial)
        val backupRestoreStateManager: BackupRestoreStateManager = mockk {
            every { backupRestoreState } returns backupRestoreStateFlow
            coEvery { emitRestoreState(any()) } coAnswers {
                backupRestoreStateFlow.emit(firstArg())
            }
        }

        val creator = getJobCreator(backupRestoreStateManager)

        val restoreJob = creator.startBackupRestoreJob(createBackupRestoreParameters())

        backupRestoreStateFlow.test {
            expectItem(BackupRestoreState.Initial)

            backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.InProgress(BackupRestoreStep.USER_DATA))
            expectItem(BackupRestoreState.InProgress(BackupRestoreStep.USER_DATA))

            backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.InProgress(BackupRestoreStep.CONTACTS))
            expectItem(BackupRestoreState.InProgress(BackupRestoreStep.CONTACTS))

            backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.InProgress(BackupRestoreStep.GROUPS))
            expectItem(BackupRestoreState.InProgress(BackupRestoreStep.GROUPS))

            backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.InProgress(BackupRestoreStep.DISTRIBUTION_LISTS))
            expectItem(BackupRestoreState.InProgress(BackupRestoreStep.DISTRIBUTION_LISTS))

            backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.InProgress(BackupRestoreStep.CSP_NONCES))
            expectItem(BackupRestoreState.InProgress(BackupRestoreStep.CSP_NONCES))

            backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.InProgress(BackupRestoreStep.POLLS))
            expectItem(BackupRestoreState.InProgress(BackupRestoreStep.POLLS))

            backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.InProgress(BackupRestoreStep.MESSAGES))
            expectItem(BackupRestoreState.InProgress(BackupRestoreStep.MESSAGES))

            backupReaderEventChannel.send(BackupReader.BackupRestoreEvent.Finished.Success)
            backupReaderEventChannel.close()
        }

        val result = restoreJob.backupRestorationFinished.await()

        assert(result is BackupRestoreResult.Success)
    }

    private fun getJobCreator(
        backupRestoreStateManager: BackupRestoreStateManager = mockk(relaxed = true),
    ) = BackupRestoreJob.Creator(
        dispatcherProvider = mockk(),
        identityStore = mockk(),
        backupRestoreStateManager = backupRestoreStateManager,
        providersProvider = mockk(),
    )

    private fun createBackupRestoreParameters() = BackupRestoreParameters(
        backupFile = mockk(),
        backupPassword = "12345678",
    )
}
