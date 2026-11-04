package ch.threema.app.threemasafe.usecases

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.threemasafe.ThreemaSafeService
import ch.threema.app.threemasafe.ThreemaSafeViewState
import ch.threema.common.DispatcherProvider
import ch.threema.common.bytes
import kotlinx.coroutines.withContext

class GetLastThreemaSafeBackupDataUseCase(
    private val preferenceService: PreferenceService,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(): ThreemaSafeViewState.LastBackupData = withContext(dispatcherProvider.io) {
        ThreemaSafeViewState.LastBackupData(
            time = preferenceService.getThreemaSafeBackupTimestamp(),
            size = preferenceService.getThreemaSafeBackupSize().takeIf { it > 0 }?.bytes,
            status = when (preferenceService.getThreemaSafeErrorCode()) {
                ThreemaSafeService.ERROR_CODE_OK -> ThreemaSafeViewState.LastBackupStatus.SUCCESS
                ThreemaSafeService.ERROR_CODE_SERVER_FAIL -> ThreemaSafeViewState.LastBackupStatus.CONNECTION_ERROR
                ThreemaSafeService.ERROR_CODE_UPLOAD_FAIL -> ThreemaSafeViewState.LastBackupStatus.UPLOAD_FAILED
                ThreemaSafeService.ERROR_CODE_SIZE_EXCEEDED -> ThreemaSafeViewState.LastBackupStatus.SIZE_LIMIT_EXCEEDED
                else -> ThreemaSafeViewState.LastBackupStatus.GENERIC_ERROR
            },
        )
    }
}
