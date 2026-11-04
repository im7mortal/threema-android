package ch.threema.app.backupcenter

import androidx.compose.runtime.Immutable

@Immutable
sealed interface LocalDataBackupViewModelEvent {
    @Immutable
    data object ProceedToBackupCreation : LocalDataBackupViewModelEvent
}
