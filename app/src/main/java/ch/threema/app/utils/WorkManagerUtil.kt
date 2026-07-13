package ch.threema.app.utils

import androidx.work.WorkInfo
import androidx.work.WorkManager
import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.ExecutionException

private val logger = getThreemaLogger("WorkManagerUtil")

object WorkManagerUtil {
    /**
     * Check if periodic work with provided [uniqueWorkName] is already scheduled or running and has the same schedule period.
     * Cancel existing work in case of error
     *
     * @param workManager An instance of the WorkManager
     * @param uniqueWorkName Unique work name
     * @param schedulePeriod scheduled period of this work
     * @return true if no periodic work with the same tag exists or the existing work has a different schedule period;
     *      false if the work already exists and has the same schedule period
     */
    @JvmStatic
    fun shouldScheduleNewWorkManagerInstance(
        workManager: WorkManager,
        uniqueWorkName: String,
        schedulePeriod: Long,
    ): Boolean {
        return try {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get().none {
                val state = it.state
                if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                    logger.debug("A job of the same name is already running or queued")
                    if (it.tags.contains(schedulePeriod.toString())) {
                        logger.debug("Job has same schedule period")
                        true
                    } else {
                        logger.debug("Job has a different schedule period")
                        false
                    }
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            logger.info("WorkManager Exception", e)
            workManager.cancelUniqueWork(uniqueWorkName)
            true
        }
    }

    @JvmStatic
    fun isWorkManagerInstanceScheduled(workManager: WorkManager, uniqueWorkName: String): Boolean {
        return try {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get().any {
                val state = it.state
                state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED
            }
        } catch (e: Exception) {
            when (e) {
                is ExecutionException, is InterruptedException -> logger.error(
                    "Could not get work info",
                    e,
                )

                else -> throw e
            }
            false
        }
    }
}
