package ch.threema.app.backuprestore.libthreema

import ch.threema.android.ResolvableString

sealed interface BackupRestoreState {
    data object Initial : BackupRestoreState
    data class InProgress(val step: BackupRestoreStep) : BackupRestoreState
    sealed interface Done : BackupRestoreState {
        data object Success : Done
        data class Failure(val errorMessage: ResolvableString) : Done
    }
}
