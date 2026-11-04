package ch.threema.app.backuprestore.libthreema

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class BackupRestoreViewModel(
    backupRestoreStateProvider: BackupRestoreStateProvider,
) : ViewModel() {

    val state: StateFlow<BackupRestoreState> =
        backupRestoreStateProvider.backupRestoreState
}
