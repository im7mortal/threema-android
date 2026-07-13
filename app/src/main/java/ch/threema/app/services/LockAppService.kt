package ch.threema.app.services

import kotlinx.coroutines.flow.StateFlow

interface LockAppService {
    val isLockingEnabled: Boolean

    val isLocked: Boolean

    fun watchLockState(): StateFlow<LockState>

    /**
     * Tries to unlock the app. If the lock mechanism is PIN, this will only succeed if the correct [pin] is provided.
     * @return True if the app was unlocked, false otherwise
     */
    fun unlock(pin: String? = null): Boolean

    fun lock()

    fun lockIfExpired()

    fun onUserActivity()

    enum class LockState {
        LOCKED,
        UNLOCKED,
    }
}
