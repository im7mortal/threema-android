package ch.threema.app.threemasafe.usecases

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.threemasafe.ThreemaSafeViewState
import ch.threema.common.DispatcherProvider
import ch.threema.common.bytes
import kotlinx.coroutines.withContext

class GetThreemaSafeConfigurationUseCase(
    private val preferenceService: PreferenceService,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(): ThreemaSafeViewState.Configuration = withContext(dispatcherProvider.io) {
        ThreemaSafeViewState.Configuration(
            server = if (preferenceService.getThreemaSafeServerInfo().isDefaultServer) {
                null
            } else {
                preferenceService.getThreemaSafeServerInfo().hostName
            },
            maxBackupSize = preferenceService.getThreemaSafeServerMaxUploadSize().bytes,
            backupRetentionDays = preferenceService.getThreemaSafeServerRetention(),
        )
    }
}
