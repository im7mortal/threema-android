package ch.threema.app.services

import kotlinx.coroutines.flow.StateFlow

interface LockAppService {

    /**
     * Whether the app is protected with a lock mechanism. It may be that the app is currently unlocked.
     */
    val isLockingEnabled: Boolean

    /**
     * Whether the app is currently locked, i.e., a lock mechanism is set up and the app has not been recently unlocked.
     */
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
