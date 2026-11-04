package ch.threema.app.backuprestore.libthreema

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ch.threema.android.ToastDuration
import ch.threema.android.buildNotification
import ch.threema.android.buildServiceIntent
import ch.threema.android.checkGrantedPermission
import ch.threema.android.getParcelableExtraCompat
import ch.threema.android.getPendingIntentForActivity
import ch.threema.android.getPendingIntentForForegroundService
import ch.threema.android.getPendingIntentForService
import ch.threema.android.showToast
import ch.threema.android.toTreeUriDocumentFile
import ch.threema.app.GlobalAppState
import ch.threema.app.R
import ch.threema.app.backupcenter.CreateLocalDataBackupActivity
import ch.threema.app.backupcenter.models.LastLocalDataBackupInfo
import ch.threema.app.backupcenter.models.LocalDataBackupPassword
import ch.threema.app.home.HomeActivity
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.awaitDisconnected
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.TimeProvider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("BackupCreateService")

class BackupCreateService : LifecycleService() {
    private val backupCreateManager: BackupCreateManager by inject()
    private val backupCreateStateManager: BackupCreateStateManager by inject()
    private val globalAppState: GlobalAppState by inject()
    private val lifetimeService: LifetimeService by inject()
    private val preferenceService: PreferenceService by inject()
    private val dispatcherProvider: DispatcherProvider by inject()
    private val timeProvider: TimeProvider by inject()
    private val notificationManagerCompat by lazy { NotificationManagerCompat.from(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent == null) {
            logger.error("Intent is null")
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.isCancelIntent()) {
            logger.info("Cancel intent received. Aborting backup creation.")
            stopSelf()
            backupCreateStateManager.updateState(BackupCreateState.Initial)
            // TODO(ANDR-5333): Ensure that the backup job is properly stopped
            return START_NOT_STICKY
        }

        val backupUri = intent.getBackupUri() ?: run {
            logger.error("Invalid input. No backup file provided.")
            stopSelf()
            return START_NOT_STICKY
        }
        val backupPassword = intent.getBackupPassword() ?: run {
            logger.error("Invalid input. No backup password provided.")
            stopSelf()
            return START_NOT_STICKY
        }
        val backupCreateParameters = BackupCreateParameters(
            backupUri = backupUri,
            backupPassword = backupPassword.passwordString,
            includeMedia = intent.getIncludeMedia(),
        )

        NotificationChannels.createOrMigrateNotificationChannels(this)
        ServiceCompat.startForeground(
            this,
            NotificationIDs.CROSS_PLATFORM_BACKUP_CREATE_ACTIVE_NOTIFICATION_ID,
            createPersistentNotification(),
            FG_SERVICE_TYPE,
        )

        lifecycleScope.launch(dispatcherProvider.worker) {
            val backupCreateResult = runCatching {
                withContext(dispatcherProvider.io) {
                    if (!backupUri.isDirectory()) {
                        error("Invalid input. Backup uri is a file instead of a directory.")
                    }
                }
                prepareAppForBackup()
                backupCreateManager
                    .startCreatingBackup(backupCreateParameters)
                    .await()
            }

            try {
                backupCreateResult.fold(
                    onSuccess = { backupResult ->
                        when (backupResult) {
                            is BackupCreateResult.Success -> {
                                logger.info("Backup completed successfully")
                                persistPassword(backupPassword)
                                persistLastBackupInfo(backupResult)
                                showBackupSuccessNotification()
                            }

                            is BackupCreateResult.Failure -> {
                                logger.warn("Backup wasn't successful")
                                showBackupErrorNotification()
                            }
                        }
                    },
                    onFailure = { throwable ->
                        if (throwable is CancellationException) {
                            throw throwable
                        }
                        // TODO(ANDR-4890): Would it make sense to report this error?
                        logger.error("Backup completed exceptionally", throwable)
                        showBackupErrorNotification()
                        backupCreateStateManager.updateState(BackupCreateState.Done.Failure)
                    },
                )
            } finally {
                cancelPersistentNotification()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun prepareAppForBackup() {
        globalAppState.startSpecialMode(GlobalAppState.SpecialMode.CROSS_PLATFORM_BACKUP_CREATE)
        withTimeoutOrNull(20.seconds) {
            lifetimeService.awaitDisconnected()
        }
            ?: error("Failed to disconnect in time")
    }

    private fun Uri.isDirectory(): Boolean =
        toTreeUriDocumentFile(applicationContext)?.isDirectory == true

    override fun onDestroy() {
        super.onDestroy()
        globalAppState.endSpecialMode(GlobalAppState.SpecialMode.CROSS_PLATFORM_BACKUP_CREATE)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createPersistentNotification(): Notification {
        return buildNotification(
            context = this,
            channelId = NotificationChannels.NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS,
        ) {
            val contentIntent = getPendingIntentForActivity(
                context = this@BackupCreateService,
                requestCode = NotificationRequestCodes.CROSS_PLATFORM_BACKUP_CREATE_IN_PROGRESS,
                intent = CreateLocalDataBackupActivity.createIntent(this@BackupCreateService),
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setContentIntent(contentIntent)
            setContentTitle(getString(R.string.backup_in_progress))
            setContentText(getString(R.string.please_wait))
            setOngoing(true)
            setSmallIcon(R.drawable.ic_notification_small)
            setLocalOnly(true)
            addAction(R.drawable.ic_close_white_24dp, getString(R.string.cancel), getCancelPendingIntent())
        }
    }

    private fun getCancelPendingIntent(): PendingIntent {
        val cancelIntent = createCancelIntent(this)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getPendingIntentForForegroundService(
                context = this,
                requestCode = NotificationRequestCodes.CROSS_PLATFORM_BACKUP_CREATE_CANCEL,
                intent = cancelIntent,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            getPendingIntentForService(
                context = this,
                requestCode = NotificationRequestCodes.CROSS_PLATFORM_BACKUP_CREATE_CANCEL,
                intent = cancelIntent,
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    private fun cancelPersistentNotification() {
        notificationManagerCompat.cancel(NotificationIDs.CROSS_PLATFORM_BACKUP_CREATE_ACTIVE_NOTIFICATION_ID)
    }

    private suspend fun persistPassword(localDataBackupPassword: LocalDataBackupPassword) = withContext(dispatcherProvider.io) {
        preferenceService.setLocalDataBackupPassword(localDataBackupPassword)
    }

    private fun persistLastBackupInfo(backupResult: BackupCreateResult.Success) {
        preferenceService.setLastLocalDataBackupInfo(
            LastLocalDataBackupInfo(
                createdAt = timeProvider.get(),
                size = backupResult.fileSize,
                mediaIncluded = backupResult.includesMedia,
                location = if (backupResult.fileLocation != null) {
                    "${backupResult.fileLocation}/${backupResult.filename}"
                } else {
                    backupResult.filename
                },
            ),
        )
    }

    private fun showBackupSuccessNotification() {
        val notification = buildNotification(
            context = this,
            channelId = NotificationChannels.NOTIFICATION_CHANNEL_ALERT,
        ) {
            val pendingIntent = getPendingIntentForActivity(
                context = this@BackupCreateService,
                requestCode = NotificationRequestCodes.HOME_ACTIVITY,
                intent = HomeActivity.createIntent(this@BackupCreateService),
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setContentIntent(pendingIntent)

            setSmallIcon(R.drawable.ic_notification_small)
            setTicker(getString(R.string.backup_or_restore_success_body))
            setContentTitle(getString(R.string.app_name))
            setDefaults(Notification.DEFAULT_LIGHTS or Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            setPriority(NotificationCompat.PRIORITY_MAX)
            setAutoCancel(true)

            val text = getString(R.string.backup_or_restore_success_body)
            setContentText(text)
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        if (!checkGrantedPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            logger.warn("Cannot show backup success notification because the permission is not granted")
            return
        }
        notificationManagerCompat.notify(NotificationIDs.CROSS_PLATFORM_BACKUP_CREATE_COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun showBackupErrorNotification() {
        val contentText = getString(R.string.backup_or_restore_error_body)
        val notification = buildNotification(
            context = this,
            channelId = NotificationChannels.NOTIFICATION_CHANNEL_ALERT,
        ) {
            // TODO(ANDR-4890): Should this point to the home activity or to a dedicated activity that shows the progress/state/errors?
            val pendingIntent = getPendingIntentForActivity(
                context = this@BackupCreateService,
                requestCode = NotificationRequestCodes.HOME_ACTIVITY,
                intent = HomeActivity.createIntent(this@BackupCreateService),
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setContentIntent(pendingIntent)

            setSmallIcon(R.drawable.ic_notification_small)
            setTicker(getString(R.string.backup_or_restore_error_body))
            setContentTitle(getString(R.string.backup_or_restore_error))
            setContentText(contentText)
            setDefaults(Notification.DEFAULT_LIGHTS or Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            setPriority(NotificationCompat.PRIORITY_MAX)
            setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            setAutoCancel(false)
        }

        if (checkGrantedPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationManagerCompat.notify(NotificationIDs.CROSS_PLATFORM_BACKUP_CREATE_COMPLETION_NOTIFICATION_ID, notification)
        } else {
            logger.warn("Cannot show backup error notification because the permission is not granted")
            showToast(R.string.backup_or_restore_error_body, ToastDuration.LONG)
        }
    }

    class Scheduler(
        private val appContext: Context,
    ) {
        fun start(backupUri: Uri, password: LocalDataBackupPassword, includeMedia: Boolean) {
            val serviceIntent = createStartIntent(appContext, backupUri, password, includeMedia)
            ContextCompat.startForegroundService(appContext, serviceIntent)
        }

        fun stop() {
            appContext.stopService(createCancelIntent(appContext))
        }
    }

    companion object {
        private val FG_SERVICE_TYPE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        private const val EXTRA_CANCEL = "CANCEL"

        private const val EXTRA_BACKUP_URI = "BACKUP_URI"
        private const val EXTRA_BACKUP_PASSWORD = "BACKUP_PASSWORD"
        private const val EXTRA_INCLUDE_MEDIA = "INCLUDE_MEDIA"

        private fun Intent.isCancelIntent(): Boolean =
            getBooleanExtra(EXTRA_CANCEL, false)

        private fun Intent.getBackupUri(): Uri? =
            getParcelableExtraCompat<Uri>(EXTRA_BACKUP_URI)

        private fun Intent.getIncludeMedia(): Boolean =
            getBooleanExtra(EXTRA_INCLUDE_MEDIA, false)

        private fun Intent.getBackupPassword(): LocalDataBackupPassword? =
            getStringExtra(EXTRA_BACKUP_PASSWORD)
                ?.let(::LocalDataBackupPassword)

        private fun createStartIntent(
            context: Context,
            backupUri: Uri,
            password: LocalDataBackupPassword,
            includeMedia: Boolean,
        ) = buildServiceIntent<BackupCreateService>(context) {
            putExtra(EXTRA_BACKUP_URI, backupUri)
            putExtra(EXTRA_BACKUP_PASSWORD, password.passwordString)
            putExtra(EXTRA_INCLUDE_MEDIA, includeMedia)
        }

        private fun createCancelIntent(context: Context) = buildServiceIntent<BackupCreateService>(context) {
            putExtra(EXTRA_CANCEL, true)
        }
    }
}
