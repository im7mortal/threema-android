package ch.threema.app.startup

import ch.threema.app.framework.BaseViewModel
import ch.threema.app.startup.models.RemoteSecretUpdateStatus
import ch.threema.app.startup.models.RemoteSecretUpdateType
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.RemoteSecretProtectionInstruction
import kotlinx.coroutines.CancellationException

class RemoteSecretProtectionUpdateViewModel(
    private val masterKeyManager: MasterKeyManager,
    private val remoteSecretProtectionUpdater: RemoteSecretProtectionUpdater,
) : BaseViewModel<RemoteSecretProtectionUpdateViewState, RemoteSecretProtectionUpdateViewModelEvent>() {
    private var isRunning = false

    override suspend fun initialize(): RemoteSecretProtectionUpdateViewState {
        runWhenActive {
            runRemoteSecretProtectionUpdate()
        }

        val updateType = when (masterKeyManager.getRemoteSecretProtectionInstruction()) {
            RemoteSecretProtectionInstruction.SHOULD_ACTIVATE -> RemoteSecretUpdateType.ACTIVATING
            RemoteSecretProtectionInstruction.SHOULD_DEACTIVATE -> RemoteSecretUpdateType.DEACTIVATING
            RemoteSecretProtectionInstruction.NO_CHANGE_NEEDED,
            null,
            -> {
                emitEvent(RemoteSecretProtectionUpdateViewModelEvent.Done)
                throw CancellationException()
            }
        }

        return RemoteSecretProtectionUpdateViewState(
            updateType = updateType,
            status = RemoteSecretUpdateStatus.IDLE,
        )
    }

    fun onClickedRetry() {
        runRemoteSecretProtectionUpdate()
    }

    private fun runRemoteSecretProtectionUpdate() = runAction {
        if (isRunning) {
            return@runAction
        }
        isRunning = true
        try {
            updateViewState {
                copy(status = RemoteSecretUpdateStatus.IN_PROGRESS)
            }

            when (remoteSecretProtectionUpdater.runRemoteSecretProtectionUpdate()) {
                RemoteSecretProtectionUpdater.RemoteSecretProtectionUpdateResult.Success -> updateViewState {
                    copy(status = RemoteSecretUpdateStatus.SUCCEEDED)
                }

                RemoteSecretProtectionUpdater.RemoteSecretProtectionUpdateResult.InvalidCredentialsError -> emitEvent(
                    RemoteSecretProtectionUpdateViewModelEvent.PromptForCredentials,
                )

                RemoteSecretProtectionUpdater.RemoteSecretProtectionUpdateResult.PassphraseRequiredError -> masterKeyManager.lockWithPassphrase()

                RemoteSecretProtectionUpdater.RemoteSecretProtectionUpdateResult.NetworkError,
                RemoteSecretProtectionUpdater.RemoteSecretProtectionUpdateResult.ServiceInstantiationError,
                RemoteSecretProtectionUpdater.RemoteSecretProtectionUpdateResult.KeyStorageError,
                -> updateViewState {
                    copy(status = RemoteSecretUpdateStatus.FAILED)
                }
            }
        } finally {
            isRunning = false
        }
    }

    fun onDismissDialog() = runAction {
        updateViewState {
            copy(status = RemoteSecretUpdateStatus.IDLE)
        }
        emitEvent(RemoteSecretProtectionUpdateViewModelEvent.Done)
    }
}
