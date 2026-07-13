package ch.threema.app.services

import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.IdentityProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val logger = getThreemaLogger("LockAppServiceImpl")

class LockAppServiceImpl(
    private val lockAppTimer: LockAppTimer,
    private val preferencesService: PreferenceService,
    private val identityProvider: IdentityProvider,

) : LockAppService {
    private val lockedState = MutableStateFlow(
        if (isLockingEnabled) {
            LockAppService.LockState.LOCKED
        } else {
            LockAppService.LockState.UNLOCKED
        },
    )

    override val isLocked: Boolean
        get() = lockedState.value == LockAppService.LockState.LOCKED

    override val isLockingEnabled: Boolean
        get() = preferencesService.isAppLockEnabled() && identityProvider.getIdentity() != null

    override fun watchLockState(): StateFlow<LockAppService.LockState> =
        lockedState

    override fun unlock(pin: String?): Boolean {
        when (preferencesService.getLockMechanism()) {
            PreferenceService.LockMechanism.PIN -> if (pin == null || !preferencesService.isPinCodeCorrect(pin)) {
                logger.info("Incorrect PIN entered")
                return false
            }
            PreferenceService.LockMechanism.SYSTEM,
            PreferenceService.LockMechanism.BIOMETRIC,
            -> Unit
            PreferenceService.LockMechanism.NONE -> return true
        }

        lockAppTimer.cancel()
        updateState(LockAppService.LockState.UNLOCKED)
        return !isLocked
    }

    private fun updateState(newState: LockAppService.LockState) {
        if (this.lockedState.value == newState) {
            return
        }
        logger.info("updating locked stated from {} to {} ", this.lockedState.value, newState)
        this.lockedState.value = newState
    }

    override fun lock() {
        if (isLockingEnabled) {
            updateState(LockAppService.LockState.LOCKED)
        }
    }

    override fun lockIfExpired() {
        if (lockAppTimer.isExpired()) {
            lock()
        }
    }

    override fun onUserActivity() {
        lockAppTimer.restart()
    }
}
