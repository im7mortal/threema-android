package ch.threema.app.monitors

import ch.threema.app.monitors.MonitorController.Companion.MONITOR_RESTART_DELAY
import ch.threema.testhelpers.elapsedVirtualTime
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorControllerTest {

    @Test
    fun `monitor controller runs all monitors`() = runTest {
        val deferred1 = CompletableDeferred<Int>()
        val deferred2 = CompletableDeferred<Int>()
        val monitorController = MonitorController(
            monitorProvider = mockk {
                every { monitors } returns listOf(
                    mockMonitor {
                        delay(20.seconds)
                        deferred1.complete(123)
                        awaitCancellation()
                    },
                    mockMonitor {
                        delay(10.seconds)
                        deferred2.complete(456)
                        awaitCancellation()
                    },
                )
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        val monitorControllerJob = launch {
            monitorController.run()
        }

        assertEquals(123, deferred1.await())
        assertEquals(456, deferred2.await())

        monitorControllerJob.cancel()
    }

    @Test
    fun `monitor controller restarts failing or stopping monitors`() = runTest {
        val timeUntilFailure = 10.hours
        val timeUntilUnexpectedStopping = 10.minutes
        var counter = 0
        val monitorController = MonitorController(
            monitorProvider = mockk {
                every { monitors } returns listOf(
                    mockMonitor {
                        counter++
                        when (counter) {
                            1, 2 -> {
                                delay(timeUntilFailure)
                                throw RuntimeException("oh no")
                            }
                            3 -> {
                                delay(timeUntilUnexpectedStopping)
                            }
                            else -> awaitCancellation()
                        }
                    },
                )
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        val monitorControllerJob = launch {
            monitorController.run()
        }

        advanceUntilIdle()

        assertEquals(4, counter)
        assertEquals(2 * timeUntilFailure + timeUntilUnexpectedStopping + 3 * MONITOR_RESTART_DELAY, elapsedVirtualTime)

        monitorControllerJob.cancel()
    }

    companion object {
        private fun mockMonitor(body: suspend () -> Unit): Monitor =
            mockk<Monitor> {
                every { this@mockk.name } returns ""
                coEvery { run() } coAnswers { body() }
            }
    }
}
