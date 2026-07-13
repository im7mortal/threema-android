package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class StopwatchTest {

    @Test
    fun `measure elapsed time`() {
        val stopwatch = Stopwatch()
        assertNull(stopwatch.elapsedTime)

        stopwatch.start()
        assertNull(stopwatch.elapsedTime)

        stopwatch.stop()
        val elapsedTime = stopwatch.elapsedTime
        assertNotNull(elapsedTime)
        assertTrue(elapsedTime < 10.seconds)
    }

    @Test
    fun `incorrect use results in null`() {
        val stopwatch = Stopwatch()

        stopwatch.stop()
        assertNull(stopwatch.elapsedTime)

        stopwatch.start()
        stopwatch.stop()
        stopwatch.stop()
        assertNull(stopwatch.elapsedTime)
    }
}
