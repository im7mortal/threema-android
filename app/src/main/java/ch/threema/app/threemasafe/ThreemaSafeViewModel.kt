package ch.threema.app.threemasafe

import ch.threema.app.framework.BaseViewModel
import ch.threema.app.listeners.ThreemaSafeListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.threemasafe.usecases.GetLastThreemaSafeBackupDataUseCase
import ch.threema.app.threemasafe.usecases.GetThreemaSafeConfigurationUseCase
import kotlinx.coroutines.awaitCancellation

class ThreemaSafeViewModel(
    private val preferenceService: PreferenceService,
    private val threemaSafeService: ThreemaSafeService,
    private val getLastThreemaSafeBackupDataUseCase: GetLastThreemaSafeBackupDataUseCase,
    private val getThreemaSafeConfigurationUseCase: GetThreemaSafeConfigurationUseCase,
) : BaseViewModel<ThreemaSafeViewState, ThreemaSafeViewModelEvent>() {
    override suspend fun initialize(): ThreemaSafeViewState {
        if (!preferenceService.getThreemaSafeEnabled()) {
            emitEvent(ThreemaSafeViewModelEvent.Finish)
            awaitCancellation()
        }

        runWhenActive {
            try {
                if (!preferenceService.getThreemaSafeEnabled()) {
                    emitEvent(ThreemaSafeViewModelEvent.Finish)
                    return@runWhenActive
                }
                updateLastBackupData()
                ListenerManager.threemaSafeListeners.add(threemaSafeListener)
                awaitCancellation()
            } finally {
                ListenerManager.threemaSafeListeners.remove(threemaSafeListener)
            }
        }

        return ThreemaSafeViewState(
            lastBackupData = getLastThreemaSafeBackupDataUseCase.call(),
            configuration = getThreemaSafeConfigurationUseCase.call(),
            backUpButtonEnabled = true,
        )
    }

    private val threemaSafeListener = ThreemaSafeListener {
        updateLastBackupData()
    }

    private fun updateLastBackupData() {
        runAction {
            val lastBackupData = getLastThreemaSafeBackupDataUseCase.call()
            val configuration = getThreemaSafeConfigurationUseCase.call()
            updateViewState {
                ThreemaSafeViewState(
                    lastBackupData = lastBackupData,
                    configuration = configuration,
                    backUpButtonEnabled = true,
                )
            }
        }
    }

    fun onClickBackUpNow() = runAction {
        updateViewState {
            // There's currently no reliable way to observe the Threema Safe progress. As a best-effort workaround, we just disable
            // the button here and then re-enable it the next time the "last backup data" is updated
            copy(backUpButtonEnabled = false)
        }
        threemaSafeService.uploadNow()
        threemaSafeService.reschedulePeriodicUpload()
    }
}
