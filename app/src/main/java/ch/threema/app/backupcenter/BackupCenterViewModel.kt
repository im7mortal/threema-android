package ch.threema.app.backupcenter

import ch.threema.app.backupcenter.usecases.CheckThreemaSafeAvailableUseCase
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.threemasafe.ThreemaSafeService
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("BackupCenterViewModel")

class BackupCenterViewModel(
    private val preferenceService: PreferenceService,
    private val checkThreemaSafeAvailableUseCase: CheckThreemaSafeAvailableUseCase,
    private val threemaSafeService: ThreemaSafeService,
    private val dispatcherProvider: DispatcherProvider,
) : BaseViewModel<BackupCenterViewState, BackupCenterViewModelEvent>() {
    override suspend fun initialize(): BackupCenterViewState {
        runWhenActive {
            preferenceService.watchThreemaSafeEnabled().collect { isThreemaSafeEnabled ->
                updateViewState {
                    copy(threemaSafeEnabled = isThreemaSafeEnabled)
                }
            }
        }
        runWhenActive {
            preferenceService.watchLastLocalDataBackupInfo()
                .collect { lastLocalDataBackupInfo ->
                    updateViewState {
                        copy(lastBackupTime = lastLocalDataBackupInfo?.createdAt)
                    }
                }
        }

        return withContext(dispatcherProvider.io) {
            BackupCenterViewState(
                infoBannerVisible = !preferenceService.isBackupWarningDismissed(),
                threemaSafeVisible = checkThreemaSafeAvailableUseCase.call(),
                threemaSafeEnabled = preferenceService.getThreemaSafeEnabled(),
                lastBackupTime = preferenceService.getLastLocalDataBackupInfo()?.createdAt,
                threemaSafeDisableDialogVisible = false,
                disablingThreemaSafeInProgress = false,
            )
        }
    }

    fun onClickDismissInfoBanner() = runAction {
        preferenceService.setBackupWarningDismissed()
        updateViewState {
            copy(infoBannerVisible = false)
        }
    }

    fun onClickLocalBackup() = runAction {
        if (currentViewState.lastBackupTime == null) {
            emitEvent(BackupCenterViewModelEvent.ProceedToBackupCreation)
        } else {
            emitEvent(BackupCenterViewModelEvent.ProceedToDataBackup)
        }
    }

    fun onClickThreemaSafe() = runAction {
        if (currentViewState.threemaSafeEnabled) {
            emitEvent(BackupCenterViewModelEvent.GoToThreemaSafeDetails)
        } else {
            onChangeThreemaSafeEnabled(true)
        }
    }

    fun onChangeThreemaSafeEnabled(enabled: Boolean) = runAction {
        if (enabled == currentViewState.threemaSafeEnabled) {
            return@runAction
        }
        if (enabled) {
            emitEvent(BackupCenterViewModelEvent.GoToThreemaSafeSettings)
        } else {
            updateViewState {
                copy(threemaSafeDisableDialogVisible = true)
            }
        }
    }

    fun onConfirmDisableThreemaSafeDialog() = runAction {
        if (currentViewState.disablingThreemaSafeInProgress) {
            return@runAction
        }
        updateViewState {
            copy(threemaSafeDisableDialogVisible = false)
        }
        disableThreemaSafe()
    }

    private suspend fun disableThreemaSafe() {
        updateViewState {
            copy(disablingThreemaSafeInProgress = true)
        }
        try {
            withContext(dispatcherProvider.io) {
                threemaSafeService.deleteBackup()
            }
            threemaSafeService.setEnabled(false)
            emitEvent(BackupCenterViewModelEvent.ThreemaSafeDisabled)
        } catch (e: ThreemaException) {
            logger.error("Failed to delete Threema safe backup", e)
            emitEvent(BackupCenterViewModelEvent.ThreemaSafeDisablingFailed)
            return
        } finally {
            updateViewState {
                copy(
                    disablingThreemaSafeInProgress = false,
                )
            }
        }
    }

    fun onDismissDisableThreemaSafeDialog() = runAction {
        updateViewState {
            copy(threemaSafeDisableDialogVisible = false)
        }
    }
}
