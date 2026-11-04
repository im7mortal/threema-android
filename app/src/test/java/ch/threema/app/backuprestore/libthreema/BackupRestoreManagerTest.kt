package ch.threema.app.backuprestore.libthreema

import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertIs
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BackupRestoreManagerTest {
    @Test
    fun `a failing restore job leads to a failure backup restore result`() = runTest {
        val restoreJobMock = mockk<BackupRestoreJob>()
        coEvery { restoreJobMock.backupRestorationFinished } returns mockk<Deferred<BackupRestoreResult>> {
            coEvery { await() } returns BackupRestoreResult.Failure
        }

        val jobCreatorMock = mockk<BackupRestoreJob.Creator>()
        coEvery { jobCreatorMock.startBackupRestoreJob(any()) } returns restoreJobMock

        val restoreManager = BackupRestoreManager(jobCreatorMock)

        val result = restoreManager.restoreBackup(mockk())

        assertIs<BackupRestoreResult.Failure>(result)
    }

    @Test
    fun `a successful restore job leads to a success backup restore result`() = runTest {
        val restoreJobMock = mockk<BackupRestoreJob>()
        coEvery { restoreJobMock.backupRestorationFinished } returns mockk<Deferred<BackupRestoreResult>> {
            coEvery { await() } returns BackupRestoreResult.Success
        }

        val jobCreatorMock = mockk<BackupRestoreJob.Creator>()
        coEvery { jobCreatorMock.startBackupRestoreJob(any()) } returns restoreJobMock

        val restoreManager = BackupRestoreManager(jobCreatorMock)

        val result = restoreManager.restoreBackup(mockk())

        assertIs<BackupRestoreResult.Success>(result)
    }

    @Test
    fun `subsequent restorations can be started`() = runTest {
        val restoreJobMock = mockk<BackupRestoreJob>(relaxed = true)

        val jobCreatorMock = mockk<BackupRestoreJob.Creator>()
        coEvery { jobCreatorMock.startBackupRestoreJob(any()) } returns restoreJobMock

        val restoreManager = BackupRestoreManager(jobCreatorMock)

        // First attempt "fails"
        coEvery { restoreJobMock.backupRestorationFinished } returns mockk<Deferred<BackupRestoreResult>> {
            coEvery { await() } returns BackupRestoreResult.Failure
        }
        restoreManager.restoreBackup(mockk(relaxed = true))

        // Second attempt "succeeds"
        coEvery { restoreJobMock.backupRestorationFinished } returns mockk<Deferred<BackupRestoreResult>> {
            coEvery { await() } returns BackupRestoreResult.Success
        }
        restoreManager.restoreBackup(mockk(relaxed = true))
    }
}
