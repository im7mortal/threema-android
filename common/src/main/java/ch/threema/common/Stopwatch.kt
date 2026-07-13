package ch.threema.common

import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A utility class for measuring the passage of time. It is intended as an extension to [kotlin.time.measureTime] for situations where
 * the starting point and end point happen in different places.
 */
class Stopwatch {
    private var startTimeMark: TimeSource.Monotonic.ValueTimeMark? = null

    /**
     * The time that has elapsed between the most recent calls to [start] and [stop],
     * or null if [start] was never called or if [stop] was not called after the most recent call to [start].
     */
    var elapsedTime: Duration? = null
        private set

    fun start() {
        elapsedTime = null
        startTimeMark = TimeSource.Monotonic.markNow()
    }

    fun stop() {
        elapsedTime = startTimeMark?.elapsedNow()
        startTimeMark = null
    }
}
