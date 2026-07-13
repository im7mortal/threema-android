package ch.threema.app.backupcenter

import ch.threema.app.backupcenter.usecases.CheckThreemaSafeAvailableUseCase
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.DispatcherProvider
import java.time.Instant
import kotlinx.coroutines.withContext

class BackupCenterViewModel(
    private val preferenceService: PreferenceService,
    private val checkThreemaSafeAvailableUseCase: CheckThreemaSafeAvailableUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : BaseViewModel<BackupCenterViewState, Unit>() {
    override suspend fun initialize(): BackupCenterViewState {
        runWhenActive {
            preferenceService.watchThreemaSafeEnabled().collect { isThreemaSafeEnabled ->
                updateViewState {
                    copy(threemaSafeEnabled = isThreemaSafeEnabled)
                }
            }
        }

        return withContext(dispatcherProvider.io) {
            BackupCenterViewState(
                infoBannerVisible = !preferenceService.isBackupWarningDismissed(),
                threemaSafeVisible = checkThreemaSafeAvailableUseCase.call(),
                threemaSafeEnabled = preferenceService.getThreemaSafeEnabled(),
                // TODO(ANDR-4880): hard-coded for now, use real value once available
                lastBackupTime = Instant.ofEpochMilli(1_778_663_772_000L),
            )
        }
    }

    fun onClickDismissInfoBanner() = runAction {
        preferenceService.setBackupWarningDismissed()
        updateViewState {
            copy(infoBannerVisible = false)
        }
    }
}
