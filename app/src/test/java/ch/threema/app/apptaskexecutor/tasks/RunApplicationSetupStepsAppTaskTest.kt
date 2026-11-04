package ch.threema.app.apptaskexecutor.tasks

import ch.threema.app.protocolsteps.ApplicationSetupSteps
import ch.threema.app.startup.AppStartupMonitor
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest

@OptIn(ExperimentalCoroutinesApi::class)
class RunApplicationSetupStepsAppTaskTest : KoinTest {

    private val applicationSetupStepsMock: ApplicationSetupSteps = mockk()
    private val appStartupMonitorMock: AppStartupMonitor = mockk {
        coEvery { awaitAll() } just Runs
    }

    @BeforeTest
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { applicationSetupStepsMock }
                    single { appStartupMonitorMock }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `returns immediately when setup succeeds on first attempt`() = runTest {
        coEvery { applicationSetupStepsMock.run() } returns true

        val task = RunApplicationSetupStepsAppTask()
        task.run()

        coVerify(exactly = 1) { applicationSetupStepsMock.run() }
        assertEquals(0L, currentTime) // no delay incurred
    }

    @Test
    fun `retries with correct backoff durations when setup returns false`() = runTest {
        // Fails 8 times (consumes the entire defined backoff list), then succeeds
        coEvery { applicationSetupStepsMock.run() } returnsMany
            listOf(false, false, false, false, false, false, false, false, true)

        val task = RunApplicationSetupStepsAppTask()
        task.run()

        coVerify(exactly = 9) { applicationSetupStepsMock.run() }

        val expectedTotalDelayMillis =
            (2.seconds + 5.seconds + 10.seconds + 15.seconds + 20.seconds + 1.minutes + 2.minutes + 5.minutes)
                .inWholeMilliseconds
        assertEquals(expectedTotalDelayMillis, currentTime)
    }

    @Test
    fun `retries after exceptions and continues past them`() = runTest {
        coEvery { applicationSetupStepsMock.run() } throws RuntimeException("boom") andThen true

        val task = RunApplicationSetupStepsAppTask()
        task.run()

        coVerify(exactly = 2) { applicationSetupStepsMock.run() }
        assertEquals(2.seconds.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `backoff repeats the last duration once list is exhausted`() = runTest {
        // Fails 10 times in total: exhausts the 8-item list, then repeats 5 minutes twice more
        coEvery { applicationSetupStepsMock.run() } returnsMany
            List(10) { false } + true

        val task = RunApplicationSetupStepsAppTask()
        task.run()

        val expectedTotalDelayMillis =
            (2.seconds + 5.seconds + 10.seconds + 15.seconds + 20.seconds + 1.minutes + 2.minutes + 5.minutes + 5.minutes + 5.minutes)
                .inWholeMilliseconds
        assertEquals(expectedTotalDelayMillis, currentTime)
    }
}
