package ch.threema.app.backupcenter

import androidx.compose.runtime.Immutable

@Immutable
sealed interface CreateLocalDataBackupViewModelEvent {
    @Immutable
    data object Finish : CreateLocalDataBackupViewModelEvent
}
