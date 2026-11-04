package ch.threema.app.backupcenter.usecases

import ch.threema.app.backupcenter.CreateLocalDataBackupViewState
import ch.threema.app.backupcenter.CreateLocalDataBackupViewState.Step3InProgress.Status
import ch.threema.app.backuprestore.libthreema.BackupStep

class GetBackupProgressUseCase {
    fun call(
        backupStep: BackupStep,
        includeMedia: Boolean,
    ): CreateLocalDataBackupViewState.Step3InProgress.Progress =
        CreateLocalDataBackupViewState.Step3InProgress.Progress(
            preparingStatus = when {
                backupStep <= BackupStep.Contacts -> Status.IN_PROGRESS
                else -> Status.DONE
            },
            chatsStatus = when {
                backupStep < BackupStep.DistributionLists -> Status.WAITING
                backupStep > BackupStep.Messages -> Status.DONE
                else -> Status.IN_PROGRESS
            },
            // TODO(ANDR-5004): Derive proper state
            mediaFilesStatus = if (includeMedia) {
                when {
                    backupStep < BackupStep.DistributionLists -> Status.WAITING
                    backupStep > BackupStep.Messages -> Status.DONE
                    else -> Status.IN_PROGRESS
                }
            } else {
                null
            },
            finalizingStatus = when {
                backupStep < BackupStep.Finalizing -> Status.WAITING
                else -> Status.IN_PROGRESS
            },
        )
}
