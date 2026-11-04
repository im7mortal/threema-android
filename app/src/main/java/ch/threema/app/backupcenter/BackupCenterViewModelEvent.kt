package ch.threema.app.backupcenter

import androidx.compose.runtime.Immutable

@Immutable
sealed interface BackupCenterViewModelEvent {
    @Immutable
    data object ProceedToDataBackup : BackupCenterViewModelEvent

    @Immutable
    data object ProceedToBackupCreation : BackupCenterViewModelEvent

    @Immutable
    data object GoToThreemaSafeDetails : BackupCenterViewModelEvent

    @Immutable
    data object GoToThreemaSafeSettings : BackupCenterViewModelEvent

    @Immutable
    data object ThreemaSafeDisabled : BackupCenterViewModelEvent

    @Immutable
    data object ThreemaSafeDisablingFailed : BackupCenterViewModelEvent
}
