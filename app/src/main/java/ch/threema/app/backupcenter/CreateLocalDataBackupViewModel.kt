package ch.threema.app.backupcenter

import android.content.Context
import android.net.Uri
import ch.threema.android.toTreeUriDocumentFile
import ch.threema.app.backupcenter.models.LocalDataBackupPassword
import ch.threema.app.backupcenter.usecases.GetBackupLocationUseCase
import ch.threema.app.backupcenter.usecases.GetBackupProgressUseCase
import ch.threema.app.backupcenter.usecases.GetOrGenerateBackupPasswordUseCase
import ch.threema.app.backuprestore.libthreema.BackupCreateService
import ch.threema.app.backuprestore.libthreema.BackupCreateState
import ch.threema.app.backuprestore.libthreema.BackupCreateStateManager
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.usecases.CopyToClipboardUseCase
import kotlinx.coroutines.flow.filter

class CreateLocalDataBackupViewModel(
    private val appContext: Context,
    private val backupCreateStateManager: BackupCreateStateManager,
    private val backupCreateServiceScheduler: BackupCreateService.Scheduler,
    private val preferenceService: PreferenceService,
    private val notificationService: NotificationService,
    private val getOrGenerateBackupPasswordUseCase: GetOrGenerateBackupPasswordUseCase,
    private val copyToClipboardUseCase: CopyToClipboardUseCase,
    private val getBackupProgressUseCase: GetBackupProgressUseCase,
    private val getBackupLocationUseCase: GetBackupLocationUseCase,
) : BaseViewModel<CreateLocalDataBackupViewState, CreateLocalDataBackupViewModelEvent>() {

    private lateinit var password: LocalDataBackupPassword
    private lateinit var backupLocation: Uri
    private var isFirstBackup = false
    private var isKeyConfirmed = false
    private var includeMedia = false
    private var backupLocationName = ""

    override suspend fun initialize(): CreateLocalDataBackupViewState {
        val passwordWrapper = getOrGenerateBackupPasswordUseCase.call()
        password = passwordWrapper.password
        isFirstBackup = passwordWrapper.newlyGenerated
        isKeyConfirmed = !isFirstBackup
        includeMedia = preferenceService.getLastLocalDataBackupInfo()?.mediaIncluded ?: false
        backupLocation = getBackupLocationUseCase.call()
        updateBackupLocationName()

        runWhenActive {
            backupCreateStateManager.backupCreateState
                .filter { it != BackupCreateState.Initial }
                .collect { backupState ->
                    updateViewState {
                        copy(step = backupState.toStep(step))
                    }
                }
        }

        val backupState = backupCreateStateManager.backupCreateState.value
        return CreateLocalDataBackupViewState(step = backupState.toStep(previousStep = null))
    }

    private fun updateBackupLocationName() {
        backupLocationName = backupLocation.toTreeUriDocumentFile(appContext)?.name ?: ""
    }

    private fun BackupCreateState.toStep(previousStep: CreateLocalDataBackupViewState.Step?): CreateLocalDataBackupViewState.Step =
        when (this) {
            BackupCreateState.Initial -> {
                if (isFirstBackup) {
                    CreateLocalDataBackupViewState.Step1RecoveryKey(
                        password = password,
                    )
                } else {
                    createStep2State()
                }
            }
            is BackupCreateState.Done.Failure -> {
                CreateLocalDataBackupViewState.Step3Error
            }
            is BackupCreateState.Done.Success -> {
                CreateLocalDataBackupViewState.Step3Done(
                    includeMedia = includeMedia,
                )
            }
            is BackupCreateState.InProgress -> {
                CreateLocalDataBackupViewState.Step3InProgress(
                    progress = getBackupProgressUseCase.call(backupStep, includeMedia),
                    showAbortDialog = (previousStep as? CreateLocalDataBackupViewState.Step3InProgress)?.showAbortDialog == true,
                )
            }
        }

    fun onClickBack() = runAction {
        when (currentViewState.step) {
            is CreateLocalDataBackupViewState.Step1RecoveryKey -> {
                finish()
            }
            is CreateLocalDataBackupViewState.Step1ConfirmKey -> {
                updateViewState {
                    copy(
                        step = CreateLocalDataBackupViewState.Step1RecoveryKey(
                            password = password,
                        ),
                    )
                }
            }
            is CreateLocalDataBackupViewState.Step2 -> {
                if (isFirstBackup) {
                    updateViewState {
                        copy(
                            step = CreateLocalDataBackupViewState.Step1RecoveryKey(
                                password = password,
                            ),
                        )
                    }
                } else {
                    finish()
                }
            }
            is CreateLocalDataBackupViewState.Step3InProgress -> {
                updateAbortDialogState(visible = true)
            }
            is CreateLocalDataBackupViewState.Step3Done,
            is CreateLocalDataBackupViewState.Step3Error,
            -> {
                finish()
            }
        }
    }

    private fun getExpectedPasswordConfirmation() =
        password.passwordString.takeLast(4)

    fun onClickCopy() = runAction {
        val step1 = (currentViewState.step as? CreateLocalDataBackupViewState.Step1RecoveryKey)
            ?: return@runAction
        copyToClipboardUseCase.call(step1.password.formatted)
    }

    fun onChangeKeyConfirmationInput(input: String) = runAction {
        val step = (currentViewState.step as? CreateLocalDataBackupViewState.Step1ConfirmKey)
            ?: return@runAction
        updateViewState {
            copy(
                step = step.copy(input = input),
            )
        }
    }

    fun onChangeIncludeMedia(includeMedia: Boolean) = runAction {
        val step = (currentViewState.step as? CreateLocalDataBackupViewState.Step2)
            ?: return@runAction
        this@CreateLocalDataBackupViewModel.includeMedia = includeMedia
        updateViewState {
            copy(
                step = step.copy(includeMedia = includeMedia),
            )
        }
    }

    fun onClickProceed() = runAction {
        when (currentViewState.step) {
            is CreateLocalDataBackupViewState.Step1RecoveryKey -> {
                if (isKeyConfirmed) {
                    goToStep2()
                } else {
                    updateViewState {
                        copy(
                            step = CreateLocalDataBackupViewState.Step1ConfirmKey(
                                expectedInput = getExpectedPasswordConfirmation(),
                            ),
                        )
                    }
                }
            }
            is CreateLocalDataBackupViewState.Step1ConfirmKey -> {
                isKeyConfirmed = true
                goToStep2()
            }
            is CreateLocalDataBackupViewState.Step2 -> {
                emitEvent(CreateLocalDataBackupViewModelEvent.PromptForBatteryOptimizationsDisabling)
            }
            is CreateLocalDataBackupViewState.Step3InProgress,
            is CreateLocalDataBackupViewState.Step3Error,
            -> {
                // not possible
            }
            is CreateLocalDataBackupViewState.Step3Done -> {
                finish()
            }
        }
    }

    private suspend fun goToStep2() {
        backupCreateStateManager.updateState(BackupCreateState.Initial)
        updateViewState {
            copy(step = createStep2State())
        }
    }

    private fun createStep2State() =
        CreateLocalDataBackupViewState.Step2(
            isFirstBackup = isFirstBackup,
            includeMedia = includeMedia,
            backupLocationName = backupLocationName,
            passwordSnippet = password.snippet,
        )

    private suspend fun finish() {
        emitEvent(CreateLocalDataBackupViewModelEvent.Finish)
        notificationService.cancelBackupCompletionNotification()
        backupCreateStateManager.updateState(BackupCreateState.Initial)
    }

    fun onBatteryOptimizationsDisabledOrDismissed() = runAction {
        updateViewState {
            copy(
                step = CreateLocalDataBackupViewState.Step3InProgress(
                    progress = CreateLocalDataBackupViewState.Step3InProgress.Progress(
                        preparingStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                        mediaFilesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING.takeIf { includeMedia },
                        chatsStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                        finalizingStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                    ),
                    showAbortDialog = false,
                ),
            )
        }
        startBackup()
    }

    private fun startBackup() {
        backupCreateServiceScheduler.start(backupLocation, password, includeMedia)
    }

    fun onDismissAbortDialog() = runAction {
        updateAbortDialogState(visible = false)
    }

    private suspend fun updateAbortDialogState(visible: Boolean) {
        updateViewState {
            if (step is CreateLocalDataBackupViewState.Step3InProgress) {
                copy(
                    step = step.copy(showAbortDialog = visible),
                )
            } else {
                this
            }
        }
    }

    fun onConfirmAbortDialog() = runAction {
        backupCreateServiceScheduler.stop()
        goToStep2()
    }

    fun onClickTryAgain() = runAction {
        notificationService.cancelBackupCompletionNotification()
        goToStep2()
    }

    fun onClickSelectDirectory() = runAction {
        emitEvent(
            CreateLocalDataBackupViewModelEvent.PickDirectory(previousUri = backupLocation),
        )
    }

    fun onDirectorySelected(uri: Uri) = runAction {
        backupLocation = uri
        updateBackupLocationName()
        updateViewState {
            copy(
                step = (step as? CreateLocalDataBackupViewState.Step2)
                    ?.copy(backupLocationName = backupLocationName)
                    ?: step,
            )
        }
        preferenceService.setLocalDataBackupLocation(uri)
    }

    fun onRequestNewPassword() = runAction {
        isFirstBackup = true
        isKeyConfirmed = false
        password = getOrGenerateBackupPasswordUseCase.call(forceNew = true).password
        updateViewState {
            CreateLocalDataBackupViewState(
                step = CreateLocalDataBackupViewState.Step1RecoveryKey(
                    password = password,
                ),
            )
        }
    }
}
