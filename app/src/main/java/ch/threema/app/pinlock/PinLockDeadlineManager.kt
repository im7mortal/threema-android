package ch.threema.app.pinlock

import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.TimeProvider
import ch.threema.common.plus
import java.time.Instant
import kotlin.ranges.coerceAtMost

class PinLockDeadlineManager(
    private val timeProvider: TimeProvider,
    private val preferenceService: PreferenceService,
    private val lockoutTimeoutProvider: LockoutTimeoutProvider,
) {
    var lockoutDeadline: Instant?
        get() = (preferenceService.getLockoutDeadline())
            ?.let { deadline ->
                val now = timeProvider.get()
                deadline
                    .takeIf { it > now }
                    ?.coerceAtMost(now + lockoutTimeoutProvider.getCurrentLockoutTimeout())
            }
        private set(value) {
            preferenceService.setLockoutDeadline(value)
        }

    fun onCorrectPinEntered() {
        lockoutDeadline = null
        lockoutTimeoutProvider.resetTimeout()
    }

    fun onMaxAttemptsReached() {
        lockoutDeadline = timeProvider.get() + lockoutTimeoutProvider.getCurrentLockoutTimeout()
        lockoutTimeoutProvider.increaseTimeout()
    }
}
