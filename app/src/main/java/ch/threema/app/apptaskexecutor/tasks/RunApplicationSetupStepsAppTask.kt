package ch.threema.app.apptaskexecutor.tasks

import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.protocolsteps.ApplicationSetupSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.repeatingLast
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("RunApplicationSetupStepsAppTask")

class RunApplicationSetupStepsAppTask : PersistableAppTask, KoinComponent {
    private val applicationSetupSteps: ApplicationSetupSteps by inject()
    private val backoffIterator = listOf(
        2.seconds,
        5.seconds,
        10.seconds,
        15.seconds,
        20.seconds,
        1.minutes,
        2.minutes,
        5.minutes,
    )
        .repeatingLast()
        .iterator()

    override suspend fun run() {
        while (true) {
            awaitAppFullyReady()

            try {
                when (applicationSetupSteps.run()) {
                    true -> return
                    false -> logger.warn("Application setup steps not successful")
                }
            } catch (e: Exception) {
                logger.error("Could not execute application setup steps", e)
            }
            delay(backoffIterator.next())
        }
    }

    override fun serialize(): AppTaskData =
        RunApplicationSetupStepsAppTaskData

    @Serializable
    private object RunApplicationSetupStepsAppTaskData : AppTaskData {
        override fun createTask(): PersistableAppTask =
            RunApplicationSetupStepsAppTask()
    }
}
