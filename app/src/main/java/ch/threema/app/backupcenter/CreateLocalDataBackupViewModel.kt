package ch.threema.app.backupcenter

import ch.threema.app.backupcenter.models.BackupPassword
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.usecases.CopyToClipboardUseCase
import java.security.SecureRandom
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class CreateLocalDataBackupViewModel(
    private val secureRandom: SecureRandom,
    private val copyToClipboardUseCase: CopyToClipboardUseCase,
) : BaseViewModel<CreateLocalDataBackupViewState, CreateLocalDataBackupViewModelEvent>() {

    // TODO(ANDR-4880): Probably lateinit isn't the best choice here, consider storing the password in memory differently
    private lateinit var password: BackupPassword
    private var includeMedia: Boolean = false
    private var mockBackupJob: Job? = null

    override suspend fun initialize(): CreateLocalDataBackupViewState {
        // TODO(ANDR-4880): Check if there is already a persisted password, and if so, use that one
        password = BackupPassword.generate(secureRandom)

        // TODO(ANDR-4880): Check if there is already a backup running, and if so, initialize the state accordingly
        return CreateLocalDataBackupViewState(
            step = CreateLocalDataBackupViewState.Step1RecoveryKey(
                password = password,
            ),
        )
    }

    fun onClickBack() = runAction {
        mockBackupJob?.cancel()
        mockBackupJob = null
        when (currentViewState.step) {
            is CreateLocalDataBackupViewState.Step1RecoveryKey -> {
                emitEvent(CreateLocalDataBackupViewModelEvent.Finish)
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
                updateViewState {
                    copy(
                        step = CreateLocalDataBackupViewState.Step1ConfirmKey(
                            expectedInput = getExpectedPasswordConfirmation(),
                        ),
                    )
                }
            }
            is CreateLocalDataBackupViewState.Step3InProgress -> {
                // TODO(ANDR-4880): Cancel the backup process, ask for confirmation first
                updateViewState {
                    copy(
                        step = CreateLocalDataBackupViewState.Step2(
                            includeMedia = includeMedia,
                        ),
                    )
                }
            }
            is CreateLocalDataBackupViewState.Step3Done -> {
                emitEvent(CreateLocalDataBackupViewModelEvent.Finish)
            }
        }
    }

    private fun getExpectedPasswordConfirmation() =
        password.password.takeLast(4)

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

        // TODO(ANDR-4880): Persist this setting
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
                updateViewState {
                    copy(
                        step = CreateLocalDataBackupViewState.Step1ConfirmKey(
                            expectedInput = getExpectedPasswordConfirmation(),
                        ),
                    )
                }
            }
            is CreateLocalDataBackupViewState.Step1ConfirmKey -> {
                updateViewState {
                    copy(
                        step = CreateLocalDataBackupViewState.Step2(
                            includeMedia = includeMedia,
                        ),
                    )
                }
            }
            is CreateLocalDataBackupViewState.Step2 -> {
                startMockBackup()
            }
            is CreateLocalDataBackupViewState.Step3InProgress -> {
                // not possible
            }
            is CreateLocalDataBackupViewState.Step3Done -> {
                emitEvent(CreateLocalDataBackupViewModelEvent.Finish)
            }
        }
    }

    // TODO(ANDR-4880): Start the real backup
    private fun startMockBackup() {
        mockBackupJob?.cancel()
        mockBackupJob = launchAction {
            updateViewState {
                copy(
                    step = CreateLocalDataBackupViewState.Step3InProgress(
                        messagesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                        mediaFilesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING.takeIf { includeMedia },
                        encryptionStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                        finalizingStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                    ),
                )
            }
            delay(1.seconds)
            updateViewState {
                copy(
                    step = CreateLocalDataBackupViewState.Step3InProgress(
                        messagesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.IN_PROGRESS,
                        mediaFilesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING.takeIf { includeMedia },
                        encryptionStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                        finalizingStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                    ),
                )
            }
            delay(4.seconds)
            updateViewState {
                copy(
                    step = CreateLocalDataBackupViewState.Step3InProgress(
                        messagesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.DONE,
                        mediaFilesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING.takeIf { includeMedia },
                        encryptionStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                        finalizingStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                    ),
                )
            }
            delay(1.seconds)
            if (includeMedia) {
                updateViewState {
                    copy(
                        step = CreateLocalDataBackupViewState.Step3InProgress(
                            messagesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.DONE,
                            mediaFilesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.IN_PROGRESS,
                            encryptionStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                            finalizingStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                        ),
                    )
                }
                delay(10.seconds)
            }
            updateViewState {
                copy(
                    step = CreateLocalDataBackupViewState.Step3InProgress(
                        messagesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.DONE,
                        mediaFilesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.DONE.takeIf { includeMedia },
                        encryptionStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.IN_PROGRESS,
                        finalizingStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.WAITING,
                    ),
                )
            }
            delay(3.seconds)
            updateViewState {
                copy(
                    step = CreateLocalDataBackupViewState.Step3InProgress(
                        messagesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.DONE,
                        mediaFilesStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.DONE.takeIf { includeMedia },
                        encryptionStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.DONE,
                        finalizingStatus = CreateLocalDataBackupViewState.Step3InProgress.Status.IN_PROGRESS,
                    ),
                )
            }
            delay(1.seconds)
            updateViewState {
                copy(
                    step = CreateLocalDataBackupViewState.Step3Done(
                        includeMedia = includeMedia,
                    ),
                )
            }
        }
    }
}
