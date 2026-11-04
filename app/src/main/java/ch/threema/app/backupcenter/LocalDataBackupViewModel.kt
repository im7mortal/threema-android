package ch.threema.app.backupcenter

import ch.threema.app.backupcenter.models.LastLocalDataBackupInfo
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

class LocalDataBackupViewModel(
    private val preferenceService: PreferenceService,
    private val dispatcherProvider: DispatcherProvider,
) : BaseViewModel<LocalDataBackupViewState, LocalDataBackupViewModelEvent>() {
    override suspend fun initialize(): LocalDataBackupViewState = withContext(dispatcherProvider.io) {
        val lastBackupInfo = preferenceService.getLastLocalDataBackupInfo()
            ?: run {
                // If no backup has ever been created, this screen will be skipped
                emitEvent(LocalDataBackupViewModelEvent.ProceedToBackupCreation)
                awaitCancellation()
            }

        runWhenActive {
            preferenceService.watchLastLocalDataBackupInfo()
                .collect { lastLocalDataBackupInfo ->
                    lastLocalDataBackupInfo
                        ?.toViewState()
                        ?.let { newViewState ->
                            updateViewState { newViewState }
                        }
                }
        }

        lastBackupInfo.toViewState()
    }

    private fun LastLocalDataBackupInfo.toViewState() =
        LocalDataBackupViewState(
            lastBackupData = LocalDataBackupViewState.LastBackupData(
                time = createdAt,
                size = size,
                mediaIncluded = mediaIncluded,
                location = location,
            ),
        )
}
