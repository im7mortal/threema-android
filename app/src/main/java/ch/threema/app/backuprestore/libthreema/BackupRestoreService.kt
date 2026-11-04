package ch.threema.app.backuprestore.libthreema

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ch.threema.android.ResourceIdString
import ch.threema.android.buildNotification
import ch.threema.android.buildServiceIntent
import ch.threema.android.getPendingIntentForActivity
import ch.threema.android.getPendingIntentForForegroundService
import ch.threema.android.getPendingIntentForService
import ch.threema.android.getSerializableExtraCompat
import ch.threema.app.BuildFlavor
import ch.threema.app.GlobalAppState
import ch.threema.app.R
import ch.threema.app.apptaskexecutor.AppTaskExecutor
import ch.threema.app.apptaskexecutor.tasks.RunApplicationSetupStepsAppTask
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.app.reset.ResetAppTask
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.awaitDisconnected
import ch.threema.app.startup.RemoteSecretProtectionUpdater
import ch.threema.app.usecases.OverrideOneTimeHintsUseCase
import ch.threema.app.work.WorkSync
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.takeUnlessEmpty
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.RemoteSecretCheckType
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("BackupRestoreService")

class BackupRestoreService : LifecycleService() {
    // TODO(ANDR-5148): Master key should only be persisted after a successful restore
    //  (`MasterKeyManager#persistKeyDataIfNeeded()` called in `HomeActivity`). If this is not done, restarting the app should clear any data.
    //  - Is this already working?
    //  - Is it sufficient?
    //  - Could the home activity be started when restoring a backup failed?
    //  - Will the "deleting mechanism" be triggered, when restoring a backup failed?

    private val lifetimeService: LifetimeService by inject()
    private val backupRestoreManager: BackupRestoreManager by inject()
    private val backupRestoreStateManager: BackupRestoreStateManager by inject()
    private val resetAppTask: ResetAppTask by inject()
    private val globalAppState: GlobalAppState by inject()
    private val overrideOneTimeHintsUseCase: OverrideOneTimeHintsUseCase by inject()
    private val masterKeyManager: MasterKeyManager by inject()
    private val remoteSecretProtectionUpdater: RemoteSecretProtectionUpdater by inject()
    private val dispatcherProvider: DispatcherProvider by inject()
    private val workSync: WorkSync by inject()
    private val appTaskExecutor: AppTaskExecutor by inject()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.debug("onStartCommand")
        super.onStartCommand(intent, flags, startId)

        if (intent == null) {
            logger.error("Intent is null")
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.isCancelIntent()) {
            logger.info("Cancel intent received. Abort backup restoration")
            stopSelf()
            return START_NOT_STICKY
        }

        val backupFile = intent.getBackupFile() ?: run {
            logger.error("Invalid input. No backup file provided")
            stopSelf()
            return START_NOT_STICKY
        }

        val backupPassword = intent.getBackupPassword() ?: run {
            logger.error("Invalid input. No password provided")
            stopSelf()
            return START_NOT_STICKY
        }

        val backupRestoreParameters = BackupRestoreParameters(
            backupFile = backupFile,
            backupPassword = backupPassword,
        )

        NotificationChannels.createOrMigrateNotificationChannels(this)
        ServiceCompat.startForeground(
            this,
            NotificationIDs.CROSS_PLATFORM_BACKUP_RESTORE_ACTIVE_NOTIFICATION_ID,
            createPersistentNotification(),
            FG_SERVICE_TYPE,
        )

        lifecycleScope.launch {
            try {
                globalAppState.startSpecialMode(GlobalAppState.SpecialMode.CROSS_PLATFORM_BACKUP_RESTORE)
                // It is not mandatory to wait for the connection to be disconnected, as establishing a connection
                // is not possible at this stage anyway. But things might change in the future and a connection might be possible at this point.
                // Therefore, and as waiting does no harm either (the call will just return immediately), "disconnecting" is still awaited.
                lifetimeService.awaitDisconnected()
                restoreBackup(backupRestoreParameters)
            } finally {
                globalAppState.endSpecialMode(GlobalAppState.SpecialMode.CROSS_PLATFORM_BACKUP_RESTORE)
                // Restoring has finished, stop the restore service
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun Intent.isCancelIntent() = getBooleanExtra(EXTRA_ID_CANCEL, false)

    private fun Intent.getBackupFile(): File? = getSerializableExtraCompat<File>(EXTRA_ID_RESTORE_BACKUP_FILE)

    private fun Intent.getBackupPassword(): String? = getStringExtra(EXTRA_ID_RESTORE_BACKUP_PASSWORD)
        ?.takeUnlessEmpty()

    private fun createPersistentNotification(): Notification {
        return buildNotification(
            context = this,
            channelId = NotificationChannels.NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS,
        ) {
            val contentIntent = getPendingIntentForActivity(
                context = this@BackupRestoreService,
                requestCode = NotificationRequestCodes.CROSS_PLATFORM_BACKUP_RESTORE_IN_PROGRESS,
                intent = BackupRestoreProgressActivity.createIntent(this@BackupRestoreService),
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setContentIntent(contentIntent)
            // TODO(ANDR-4847): finalize text
            setContentTitle(getString(R.string.restoring_backup))
            setContentText(getString(R.string.please_wait))
            setOngoing(true)
            setSmallIcon(R.drawable.ic_notification_small)
            setLocalOnly(true)
            addAction(R.drawable.ic_close_white_24dp, getString(R.string.cancel), getCancelPendingIntent())
        }
    }

    private fun getCancelPendingIntent(): PendingIntent {
        val cancelIntent = buildServiceIntent<BackupRestoreService>(this) {
            putExtra(EXTRA_ID_CANCEL, true)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getPendingIntentForForegroundService(
                context = this,
                requestCode = NotificationRequestCodes.CROSS_PLATFORM_BACKUP_RESTORE_CANCEL,
                intent = cancelIntent,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            getPendingIntentForService(
                context = this,
                requestCode = NotificationRequestCodes.RESTORE_CANCEL,
                intent = cancelIntent,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    private suspend fun restoreBackup(backupRestoreParameters: BackupRestoreParameters) {
        val backupRestoreResult = runCatching {
            // TODO(ADNR-5060): Make sure the app is properly prepared for restoring a backup

            backupRestoreManager.restoreBackup(
                backupRestoreParameters = backupRestoreParameters,
            )
        }

        val result: BackupRestoreResult = backupRestoreResult.fold(
            onSuccess = { result -> result },
            onFailure = { throwable ->
                logger.error("Restoring backup failed exceptionally", throwable)

                val message = when (throwable) {
                    is CancellationException -> ResourceIdString(resId = R.string.local_data_backup_restore_canceled)
                    else -> ResourceIdString(resId = R.string.local_data_backup_restore_unexpected_exception)
                }

                backupRestoreStateManager.emitRestoreState(
                    state = BackupRestoreState.Done.Failure(
                        errorMessage = message,
                    ),
                )

                BackupRestoreResult.Failure
            },
        )

        when (result) {
            is BackupRestoreResult.Failure -> {
                logger.error("Backup restoration failed")
                launchAppReset()
            }
            is BackupRestoreResult.Success -> {
                logger.info("Backup restoration completed successfully")
                overrideOneTimeHintsUseCase.call()
                if (BuildFlavor.IS_WORK) {
                    performWorkSync()
                }
                scheduleApplicationSetupSteps()
                persistMasterKey()
            }
        }
    }

    /**
     * This will reset all app data without terminating the app process. This is launched in the [GlobalScope]
     * as resetting the app must not depend on this services' lifecycle.
     *
     * This should only be used in case of a failed restoration to reset the already restored data and still be able to display the UI.
     * To continue using the app, it must be restarted.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun launchAppReset() {
        GlobalScope.launch(dispatcherProvider.worker) {
            try {
                resetAppTask.execute(mode = ResetAppTask.Mode.PARTIAL)
            } catch (e: Exception) {
                logger.error("Failed to reset app", e)

                // Deletion may have partially succeeded, so the app may be in an inconsistent state now.
                // Therefore, it is safer to close the app entirely.
                exitProcess(0)
            }
        }
            .join()
    }

    private suspend fun performWorkSync() {
        // We try to perform the work sync five times until we abort
        repeat(5) { attempt ->
            val workSyncResult = workSync.run(forceAppLogoFetch = true)
            when (workSyncResult) {
                WorkSync.WorkSyncResult.Success -> return
                WorkSync.WorkSyncResult.MissingCredentials -> error("Credentials are missing")
                WorkSync.WorkSyncResult.Fetch2Failed -> delay((1 shl attempt).seconds)
                WorkSync.WorkSyncResult.Fetch2Unauthorized -> error("Fetch2 unauthorized")
                WorkSync.WorkSyncResult.ContactUpdateFailed -> delay((1 shl attempt).seconds)
            }
        }
        error("Work sync wasn't successful")
    }

    private suspend fun persistMasterKey() {
        if (masterKeyManager.shouldUpdateRemoteSecretProtectionState(RemoteSecretCheckType.APP_STARTUP)) {
            // Note that in case this does not succeed, the HomeActivity will perform the check as well and redirect the user to the
            // RemoteSecretProtectionUpdateActivity. This activity will then display more information/progress to the user. If this happens,
            // there is only a higher risk of losing the restored data in case the app gets killed before the master key has been persisted.
            remoteSecretProtectionUpdater.runRemoteSecretProtectionUpdate()
        } else {
            try {
                withContext(dispatcherProvider.io) {
                    masterKeyManager.persistKeyDataIfNeeded()
                }
            } catch (e: Exception) {
                logger.error("Failed to persist master key", e)
            }
        }
    }

    private suspend fun scheduleApplicationSetupSteps() {
        appTaskExecutor.persistAndScheduleTask(
            RunApplicationSetupStepsAppTask(),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    class Scheduler(
        private val appContext: Context,
    ) {
        fun start(file: File, password: String) {
            val serviceIntent = buildServiceIntent<BackupRestoreService>(
                context = appContext,
            ) {
                putExtra(EXTRA_ID_RESTORE_BACKUP_FILE, file)
                putExtra(EXTRA_ID_RESTORE_BACKUP_PASSWORD, password)
            }
            ContextCompat.startForegroundService(appContext, serviceIntent)
        }
    }

    companion object {
        private val FG_SERVICE_TYPE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        private const val EXTRA_ID_CANCEL = "EXTRA_ID_CANCEL"

        private const val EXTRA_ID_RESTORE_BACKUP_FILE = "EXTRA_ID_RESTORE_BACKUP_FILE"
        private const val EXTRA_ID_RESTORE_BACKUP_PASSWORD = "EXTRA_ID_RESTORE_BACKUP_PASSWORD"
    }
}
