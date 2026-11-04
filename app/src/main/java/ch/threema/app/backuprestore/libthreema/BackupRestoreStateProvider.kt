package ch.threema.app.backuprestore.libthreema

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface BackupRestoreStateProvider {
    val backupRestoreState: StateFlow<BackupRestoreState>
}

class BackupRestoreStateManager : BackupRestoreStateProvider {
    private val _backupRestoreState = MutableStateFlow<BackupRestoreState>(BackupRestoreState.Initial)

    override val backupRestoreState = _backupRestoreState.asStateFlow()

    suspend fun emitRestoreState(state: BackupRestoreState) {
        _backupRestoreState.emit(state)
    }
}
