package ch.threema.app.backuprestore.libthreema

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackupCreateStateManager {
    private val _backupCreateState = MutableStateFlow<BackupCreateState>(BackupCreateState.Initial)

    val backupCreateState: StateFlow<BackupCreateState> =
        _backupCreateState.asStateFlow()

    fun updateState(state: BackupCreateState) {
        _backupCreateState.value = state
    }
}
