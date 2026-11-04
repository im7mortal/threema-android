package ch.threema.app.backupcenter.usecases

import ch.threema.app.backupcenter.CreateLocalDataBackupViewState
import ch.threema.app.backupcenter.CreateLocalDataBackupViewState.Step3InProgress.Status
import ch.threema.app.backuprestore.libthreema.BackupStep
import kotlin.test.Test
import kotlin.test.assertEquals

class GetBackupProgressUseCaseTest {

    @Test
    fun `step is user-data, without media`() {
        assertEquals(
            CreateLocalDataBackupViewState.Step3InProgress.Progress(
                preparingStatus = Status.IN_PROGRESS,
                chatsStatus = Status.WAITING,
                mediaFilesStatus = null,
                finalizingStatus = Status.WAITING,
            ),
            GetBackupProgressUseCase().call(
                backupStep = BackupStep.UserData,
                includeMedia = false,
            ),
        )
    }

    @Test
    fun `step is contacts, without media`() {
        assertEquals(
            CreateLocalDataBackupViewState.Step3InProgress.Progress(
                preparingStatus = Status.IN_PROGRESS,
                chatsStatus = Status.WAITING,
                mediaFilesStatus = null,
                finalizingStatus = Status.WAITING,
            ),
            GetBackupProgressUseCase().call(
                backupStep = BackupStep.Contacts,
                includeMedia = false,
            ),
        )
    }

    @Test
    fun `step is distribution-lists, without media`() {
        assertEquals(
            CreateLocalDataBackupViewState.Step3InProgress.Progress(
                preparingStatus = Status.DONE,
                chatsStatus = Status.IN_PROGRESS,
                mediaFilesStatus = null,
                finalizingStatus = Status.WAITING,
            ),
            GetBackupProgressUseCase().call(
                backupStep = BackupStep.DistributionLists,
                includeMedia = false,
            ),
        )
    }

    @Test
    fun `step is groups, without media`() {
        assertEquals(
            CreateLocalDataBackupViewState.Step3InProgress.Progress(
                preparingStatus = Status.DONE,
                chatsStatus = Status.IN_PROGRESS,
                mediaFilesStatus = null,
                finalizingStatus = Status.WAITING,
            ),
            GetBackupProgressUseCase().call(
                backupStep = BackupStep.Groups,
                includeMedia = false,
            ),
        )
    }

    @Test
    fun `step is csp-nonces, without media`() {
        assertEquals(
            CreateLocalDataBackupViewState.Step3InProgress.Progress(
                preparingStatus = Status.DONE,
                chatsStatus = Status.IN_PROGRESS,
                mediaFilesStatus = null,
                finalizingStatus = Status.WAITING,
            ),
            GetBackupProgressUseCase().call(
                backupStep = BackupStep.CspNonces,
                includeMedia = false,
            ),
        )
    }

    @Test
    fun `step is polls, without media`() {
        assertEquals(
            CreateLocalDataBackupViewState.Step3InProgress.Progress(
                preparingStatus = Status.DONE,
                chatsStatus = Status.IN_PROGRESS,
                mediaFilesStatus = null,
                finalizingStatus = Status.WAITING,
            ),
            GetBackupProgressUseCase().call(
                backupStep = BackupStep.Polls,
                includeMedia = false,
            ),
        )
    }

    @Test
    fun `step is messages, without media`() {
        assertEquals(
            CreateLocalDataBackupViewState.Step3InProgress.Progress(
                preparingStatus = Status.DONE,
                chatsStatus = Status.IN_PROGRESS,
                mediaFilesStatus = null,
                finalizingStatus = Status.WAITING,
            ),
            GetBackupProgressUseCase().call(
                backupStep = BackupStep.Messages,
                includeMedia = false,
            ),
        )
    }

    @Test
    fun `step is finalizing, without media`() {
        assertEquals(
            CreateLocalDataBackupViewState.Step3InProgress.Progress(
                preparingStatus = Status.DONE,
                chatsStatus = Status.DONE,
                mediaFilesStatus = null,
                finalizingStatus = Status.IN_PROGRESS,
            ),
            GetBackupProgressUseCase().call(
                backupStep = BackupStep.Finalizing,
                includeMedia = false,
            ),
        )
    }

    // TODO(ANDR-5004): Add tests for including media
}
