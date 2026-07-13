package ch.threema.app.pinlock

import ch.threema.app.preference.service.PreferenceService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LockoutTimeoutProvider(
    private val preferenceService: PreferenceService,
) {
    private val timeouts = listOf(
        30.seconds,
        1.minutes,
        2.minutes,
        5.minutes,
        15.minutes,
        30.minutes,
        1.hours,
    )
    private var lockoutTimeoutIndex: Int
        get() = preferenceService.getLockoutTimeoutIndex()
        set(value) = preferenceService.setLockoutTimeoutIndex(value)

    fun getCurrentLockoutTimeout(): Duration =
        timeouts.getOrNull(lockoutTimeoutIndex) ?: timeouts.last()

    fun increaseTimeout() {
        lockoutTimeoutIndex++
    }

    fun resetTimeout() {
        lockoutTimeoutIndex = 0
    }
}
