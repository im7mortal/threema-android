package ch.threema.app.services

import ch.threema.app.preference.service.PreferenceService
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

class LockAppTimer(
    private val preferencesService: PreferenceService,
    private val timeSource: TimeSource.WithComparableMarks,
    private val lockAppAlarmScheduler: LockAppAlarmScheduler,
) {
    private var lockTimerStartedAt: ComparableTimeMark? = null

    fun restart() {
        cancel()
        preferencesService.getAppLockGraceTime()?.let { graceTime ->
            lockTimerStartedAt = timeSource.markNow()
            lockAppAlarmScheduler.startTimer(graceTime)
        }
    }

    fun cancel() {
        lockTimerStartedAt = null
        lockAppAlarmScheduler.cancelTimer()
    }

    fun isExpired(): Boolean {
        val graceTime = preferencesService.getAppLockGraceTime() ?: return false
        val timerStartedAt = lockTimerStartedAt ?: return false
        return timeSource.markNow() > timerStartedAt + graceTime
    }
}
