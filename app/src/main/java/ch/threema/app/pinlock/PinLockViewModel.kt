package ch.threema.app.pinlock

import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.LockAppService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

private val logger = getThreemaLogger("PinLockViewModel")

class PinLockViewModel(
    private val lockAppService: LockAppService,
    private val preferenceService: PreferenceService,
    private val timeProvider: TimeProvider,
    private val isCheckOnly: Boolean,
    private val pinLockDeadlineManager: PinLockDeadlineManager,
) : BaseViewModel<PinLockViewState, PinLockScreenEvent>() {

    private var failedAttempts: Int
        get() = preferenceService.getLockoutAttempts()
        set(value) {
            preferenceService.setLockoutAttempts(value)
        }
    private var errorResetJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }
    private var countdownJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    override suspend fun initialize(): PinLockViewState {
        runWhenActive {
            if (!lockAppService.isLocked && !isCheckOnly) {
                cancel()
            }
            handleAttemptLockout()
            try {
                awaitCancellation()
            } finally {
                countdownJob = null
            }
        }

        return PinLockViewState()
    }

    fun onChangedPin(pin: String) = runAction {
        if (pin.length > AppConstants.MAX_PIN_LENGTH) {
            return@runAction
        }
        updateViewState {
            copy(pin = pin)
        }
    }

    fun onClickSubmit() = runAction {
        val pin = currentViewState.pin
        if (pin.isEmpty()) {
            return@runAction
        }
        if (lockAppService.unlock(pin)) {
            logger.info("Correct PIN entered")
            failedAttempts = 0
            pinLockDeadlineManager.onCorrectPinEntered()
            emitEvent(PinLockScreenEvent.Unlock)
        } else {
            failedAttempts++

            if (failedAttempts > MAX_FAILED_ATTEMPTS) {
                pinLockDeadlineManager.onMaxAttemptsReached()
                logger.info("Wrong PIN entered, temporarily blocking UI")
                handleAttemptLockout()
            } else {
                logger.info("Wrong PIN entered")
                showError(error = ResourceIdString(R.string.pinentry_wrong_pin), duration = ERROR_MESSAGE_TIMEOUT)
            }
        }
    }

    private suspend fun showError(error: ResolvableString, duration: Duration? = null) {
        updateViewState {
            copy(
                pin = "",
                error = error,
            )
        }
        errorResetJob = null
        duration?.let {
            errorResetJob = launchAction {
                delay(duration)
                updateViewState {
                    copy(error = null)
                }
            }
        }
    }

    private suspend fun handleAttemptLockout() {
        val deadline = pinLockDeadlineManager.lockoutDeadline ?: timeProvider.get()
        updateViewState {
            copy(
                pinEntryEnabled = false,
            )
        }
        countdownJob = launchAction {
            var seconds = (deadline - timeProvider.get()).inWholeSeconds
            while (seconds > 0) {
                showError(error = { context -> context.getString(R.string.too_many_incorrect_attempts, seconds.toString()) })
                delay(1.seconds)
                seconds--
            }

            updateViewState {
                copy(
                    pinEntryEnabled = true,
                    error = null,
                )
            }
            failedAttempts = 0
        }
    }

    fun onClickCancel() = runAction {
        cancel()
    }

    fun onPressBack() = runAction {
        cancel()
    }

    private suspend fun cancel() {
        if (isCheckOnly) {
            emitEvent(PinLockScreenEvent.Cancel)
        } else {
            emitEvent(PinLockScreenEvent.NavigateToLauncher)
        }
    }

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 3
        private val ERROR_MESSAGE_TIMEOUT = 3.seconds
    }
}
