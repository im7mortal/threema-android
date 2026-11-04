package ch.threema.app.workers

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import ch.threema.android.ToastDuration
import ch.threema.android.buildNotification
import ch.threema.android.buildOneTimeWorkRequest
import ch.threema.android.buildPeriodicWorkRequest
import ch.threema.android.setConstraints
import ch.threema.android.setInputData
import ch.threema.android.showToast
import ch.threema.app.BuildFlavor
import ch.threema.app.GlobalAppState
import ch.threema.app.R
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.WorkManagerUtil
import ch.threema.app.work.WorkSync
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.IdentityProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("WorkSyncWorker")

class WorkSyncWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {
    private val globalAppState: GlobalAppState by inject()
    private val workSync: WorkSync by inject()
    private val identityProvider: IdentityProvider by inject()

    override suspend fun doWork(): Result {
        if (!BuildFlavor.IS_WORK) {
            logger.error("Not allowed to run in a non-work environment")
            return Result.failure()
        }
        if (globalAppState.specialMode != null) {
            return Result.retry()
        }
        awaitAppFullyReadyWithTimeout(timeout = 20.seconds)
            ?: return Result.failure()

        if (!identityProvider.hasIdentity()) {
            logger.info("No identity found. Aborting work sync worker.")
            return Result.failure()
        }

        val forceAppLogoFetch: Boolean = inputData.getBoolean(EXTRA_FORCE_APP_LOGO_FETCH, false)

        val workSyncResult = workSync.run(forceAppLogoFetch = forceAppLogoFetch)

        return when (workSyncResult) {
            WorkSync.WorkSyncResult.Success -> Result.success()
            WorkSync.WorkSyncResult.MissingCredentials,
            WorkSync.WorkSyncResult.Fetch2Failed,
            WorkSync.WorkSyncResult.ContactUpdateFailed,
            -> Result.failure()

            WorkSync.WorkSyncResult.Fetch2Unauthorized -> {
                context.showToast(R.string.fetch2_failure, duration = ToastDuration.LONG)
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = buildNotification(context, NotificationChannels.NOTIFICATION_CHANNEL_WORK_SYNC) {
            setSound(null)
            setSmallIcon(R.drawable.ic_sync_notification)
            setContentTitle(context.getString(R.string.wizard1_sync_work))
            setProgress(0, 0, true)
            setPriority(NotificationCompat.PRIORITY_LOW)
            setAutoCancel(true)
            setLocalOnly(true)
            setOnlyAlertOnce(true)
            setVisibility(NotificationCompat.VISIBILITY_SECRET)
        }

        return ForegroundInfo(NotificationIDs.WORK_SYNC_NOTIFICATION_ID, notification)
    }

    class Scheduler(
        private val workManager: WorkManager,
        private val preferenceService: PreferenceService,
    ) {
        fun schedulePeriodicWorkSync() {
            if (!BuildFlavor.IS_WORK) {
                logger.debug("Do not start work sync worker in non-work build")
                return
            }

            val schedulePeriod = preferenceService.getWorkSyncCheckInterval()
            logger.info("Scheduling periodic work sync. Schedule period: {}", schedulePeriod)

            try {
                val policy = if (WorkManagerUtil.shouldScheduleNewWorkManagerInstance(
                        workManager,
                        WorkerNames.WORKER_PERIODIC_WORK_SYNC,
                        schedulePeriod.inWholeMilliseconds,
                    )
                ) {
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                }
                logger.info(
                    "{}: {} existing periodic work",
                    WorkerNames.WORKER_PERIODIC_WORK_SYNC,
                    policy,
                )
                val workRequest = buildPeriodicWorkRequest(schedulePeriod)
                workManager.enqueueUniquePeriodicWork(
                    WorkerNames.WORKER_PERIODIC_WORK_SYNC,
                    policy,
                    workRequest,
                )
            } catch (e: IllegalStateException) {
                logger.error("Unable to schedule periodic work sync work", e)
            }
        }

        suspend fun cancelPeriodicWorkSyncAwait() {
            logger.info("Cancelling periodic work sync")
            val result = workManager.cancelUniqueWork(WorkerNames.WORKER_PERIODIC_WORK_SYNC).await()
            logger.info("Cancelled periodic work sync, result={}", result)
        }

        private fun buildOneTimeWorkRequest(
            forceAppLogoFetch: Boolean,
            tag: String?,
        ): OneTimeWorkRequest =
            buildOneTimeWorkRequest<WorkSyncWorker> {
                setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                setInputData {
                    putBoolean(EXTRA_FORCE_APP_LOGO_FETCH, forceAppLogoFetch)
                }
                if (tag != null) {
                    addTag(tag)
                }
            }

        private fun buildPeriodicWorkRequest(schedulePeriodMs: Duration): PeriodicWorkRequest =
            buildPeriodicWorkRequest<WorkSyncWorker>(
                repeatInterval = schedulePeriodMs,
            ) {
                setConstraints {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                }
                addTag(schedulePeriodMs.inWholeMilliseconds.toString())
                setInputData {
                    putBoolean(EXTRA_FORCE_APP_LOGO_FETCH, false)
                }
            }

        /**
         * Start a one time work sync request. Existing work will be [ExistingWorkPolicy.REPLACE]d.
         *
         * If it is required to run a callback when the request was successful or failed, use
         * `performOneTimeWorkSync(Activity, Runnable, Runnable)`.
         */
        fun performOneTimeWorkSync(
            forceAppLogoFetch: Boolean,
            tag: String?,
        ) {
            val workRequest = buildOneTimeWorkRequest(forceAppLogoFetch, tag)
            workManager.enqueueUniqueWork(
                WorkerNames.WORKER_WORK_SYNC,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
        }

        /**
         * Start a one time work sync request.
         *
         * @param onSuccess is run when the work sync request was successful
         * @param onFail    is run when the work sync request was unsuccessful
         */
        fun performOneTimeWorkSync(
            activity: AppCompatActivity,
            onSuccess: Runnable,
            onFail: Runnable,
        ) {
            val workerTag = "OneTimeWorkSyncWorker"
            val workRequest = buildOneTimeWorkRequest(
                forceAppLogoFetch = true,
                tag = workerTag,
            )
            workManager.getWorkInfoByIdLiveData(workRequest.id)
                .observe(activity) { workInfo: WorkInfo? ->
                    when (workInfo?.state) {
                        WorkInfo.State.SUCCEEDED -> onSuccess.run()
                        WorkInfo.State.FAILED -> onFail.run()
                        null -> {
                            // Just log this. It is likely that a non-null work info will be observed
                            // later and the sync completes successfully.
                            logger.info("Work info is null")
                        }

                        else -> Unit
                    }
                }
            workManager.enqueueUniqueWork(
                WorkerNames.WORKER_WORK_SYNC,
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
        }
    }

    companion object {
        private const val EXTRA_FORCE_APP_LOGO_FETCH = "FORCE_UPDATE"
    }
}
