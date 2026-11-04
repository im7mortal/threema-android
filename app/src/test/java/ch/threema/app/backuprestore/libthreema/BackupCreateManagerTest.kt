package ch.threema.app.backuprestore.libthreema

import ch.threema.common.bytes
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

class BackupCreateManagerTest {

    @Test
    fun `simple creation of backup create job works`() = runTest {
        val backupFinished = CompletableDeferred<BackupCreateResult>()
        val backupCreateManager = BackupCreateManager(
            backupCreateJobCreator = createBackupCreateJobCreatorMock(
                sequenceOf(backupFinished),
            ),
        )

        val backupFinishedDeferred = backupCreateManager.startCreatingBackup(
            backupCreateParameters = mockk(),
        )

        assertEquals(backupFinished, backupFinishedDeferred)
    }

    @Test
    fun `several calls to create backup with same backup create parameters do not restart running job`() = runTest {
        val backupFinished = CompletableDeferred<BackupCreateResult>()
        val backupCreateManager = BackupCreateManager(
            backupCreateJobCreator = createBackupCreateJobCreatorMock(
                sequenceOf(backupFinished),
            ),
        )
        val backupCreateParameters: BackupCreateParameters = mockk()
        val backupFinished0 = backupCreateManager.startCreatingBackup(
            backupCreateParameters = backupCreateParameters,
        )
        val backupFinished1 = backupCreateManager.startCreatingBackup(
            backupCreateParameters = backupCreateParameters,
        )
        val backupFinished2 = backupCreateManager.startCreatingBackup(
            backupCreateParameters = backupCreateParameters,
        )

        assertEquals(backupFinished, backupFinished0)
        assertEquals(backupFinished, backupFinished1)
        assertEquals(backupFinished, backupFinished2)
    }

    @Test
    fun `new backup is created if previous has completed successfully`() = runTest {
        val expectedBackupFinished0 = CompletableDeferred<BackupCreateResult>()
        val expectedBackupFinished1 = CompletableDeferred<BackupCreateResult>()
        val backupCreateManager = BackupCreateManager(
            backupCreateJobCreator = createBackupCreateJobCreatorMock(
                sequenceOf(expectedBackupFinished0, expectedBackupFinished1),
            ),
        )
        val backupCreateParameters: BackupCreateParameters = mockk()
        val backupFinished0 = backupCreateManager.startCreatingBackup(
            backupCreateParameters = backupCreateParameters,
        )
        // Complete the first backup
        expectedBackupFinished0.complete(
            BackupCreateResult.Success(
                fileSize = 0.bytes,
                filename = "filename",
                fileLocation = "fileLocation",
                includesMedia = false,
            ),
        )

        // Now the second call should trigger creating a second backup
        val backupFinished1 = backupCreateManager.startCreatingBackup(
            backupCreateParameters = backupCreateParameters,
        )

        assertEquals(expectedBackupFinished0, backupFinished0)
        assertEquals(expectedBackupFinished1, backupFinished1)
    }

    @Test
    fun `new backup is created if previous has completed exceptionally`() = runTest {
        val expectedBackupFinished0 = CompletableDeferred<BackupCreateResult>()
        val expectedBackupFinished1 = CompletableDeferred<BackupCreateResult>()
        val backupCreateManager = BackupCreateManager(
            backupCreateJobCreator = createBackupCreateJobCreatorMock(
                sequenceOf(expectedBackupFinished0, expectedBackupFinished1),
            ),
        )
        val backupCreateParameters: BackupCreateParameters = mockk()
        val backupFinished0 = backupCreateManager.startCreatingBackup(
            backupCreateParameters = backupCreateParameters,
        )
        // Complete the first backup exceptionally
        expectedBackupFinished0.completeExceptionally(CancellationException())

        // Now the second call should trigger creating a second backup
        val backupFinished1 = backupCreateManager.startCreatingBackup(
            backupCreateParameters = backupCreateParameters,
        )

        assertEquals(expectedBackupFinished0, backupFinished0)
        assertEquals(expectedBackupFinished1, backupFinished1)
    }

    @Test
    fun `starting backup with different parameters cancels currently running backup`() = runTest {
        val expectedBackupFinished0 = CompletableDeferred<BackupCreateResult>()
        val expectedBackupFinished1 = CompletableDeferred<BackupCreateResult>()
        val backupCreateManager = BackupCreateManager(
            backupCreateJobCreator = createBackupCreateJobCreatorMock(
                sequenceOf(expectedBackupFinished0, expectedBackupFinished1),
            ),
        )
        val backupFinished0 = backupCreateManager.startCreatingBackup(
            backupCreateParameters = mockk(),
        )
        val backupFinished1 = backupCreateManager.startCreatingBackup(
            backupCreateParameters = mockk(),
        )

        assertEquals(expectedBackupFinished0, backupFinished0)
        assertEquals(expectedBackupFinished1, backupFinished1)

        assertFailsWith<CancellationException> {
            expectedBackupFinished0.await()
        }
    }

    /**
     * Note that [backupFinishedDeferreds] are used to "control" how long a job will run.
     */
    private fun createBackupCreateJobCreatorMock(
        backupFinishedDeferreds: Sequence<CompletableDeferred<BackupCreateResult>>,
    ): BackupCreateJob.Creator = mockk {
        val backupFinishedIterator = backupFinishedDeferreds.iterator()
        coEvery { startBackupCreateJob(any()) } answers {
            val nextBackupFinished = backupFinishedIterator.next()
            mockk {
                every { backupCreateParameters } returns firstArg()
                every { backupFinished } returns nextBackupFinished
                coEvery { cancelAndJoin() } answers {
                    nextBackupFinished.completeExceptionally(CancellationException("Cancelled by cancelAndJoin()"))
                }
            }
        }
    }
}
