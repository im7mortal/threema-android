package ch.threema.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ch.threema.android.DeviceInfo
import ch.threema.android.buildNotification
import ch.threema.android.getPendingIntentForActivity
import ch.threema.app.R
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.app.startup.AppStartupError
import ch.threema.app.startup.AppStartupMonitorImpl
import ch.threema.app.startup.RemoteSecretMonitorRetryController
import ch.threema.app.utils.AppRestarter
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.exceptions.BlockedByAdminException
import ch.threema.localcrypto.exceptions.RemoteSecretMonitorException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("RemoteSecretMonitorService")

class RemoteSecretMonitorService : LifecycleService(), KoinComponent {
    private val dispatcherProvider: DispatcherProvider by inject()
    private val masterKeyManager: MasterKeyManager by inject()
    private val appStartupMonitor: AppStartupMonitorImpl by inject()
    private val remoteSecretMonitorRetryController: RemoteSecretMonitorRetryController by inject()
    private val appRestarter: AppRestarter by inject()

    private var monitoringJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.debug("onStartCommand")
        super.onStartCommand(intent, flags, startId)
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NotificationIDs.REMOTE_SECRET_ACTIVE_NOTIFICATION_ID,
            createNotification(),
            FG_SERVICE_TYPE,
        )
        if (monitoringJob == null) {
            monitoringJob = startMonitoringRemoteSecret()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (!DeviceInfo.supportsNotificationChannels()) {
            return
        }
        val notificationChannel = NotificationChannel(
            NotificationChannels.NOTIFICATION_CHANNEL_REMOTE_SECRET,
            getString(R.string.remote_secret),
            NotificationManager.IMPORTANCE_LOW,
        )
            .apply {
                description = getString(R.string.remote_secret_notification_channel_description)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setSound(null, null)
            }
        NotificationManagerCompat.from(this).createNotificationChannel(notificationChannel)
    }

    private fun createNotification(): Notification =
        buildNotification(this, NotificationChannels.NOTIFICATION_CHANNEL_REMOTE_SECRET) {
            val learnMoreUrl = getString(R.string.remote_secret_learn_more_url).toUri()
            val contentIntent = getPendingIntentForActivity(
                context = this@RemoteSecretMonitorService,
                requestCode = NotificationRequestCodes.REMOTE_SECRET,
                intent = Intent(Intent.ACTION_VIEW, learnMoreUrl),
                flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setContentTitle(getString(R.string.remote_secret))
            setContentText(getString(R.string.remote_secret_notification_text))
            setSmallIcon(R.drawable.ic_notification_small)
            setLocalOnly(true)
            setContentIntent(contentIntent)
        }

    private fun startMonitoringRemoteSecret(): Job {
        logger.info("Start monitoring of remote secret")
        return lifecycleScope.launch(dispatcherProvider.worker) {
            if (!masterKeyManager.awaitIsProtectedWithRemoteSecret()) {
                logger.warn("Master key is not protected with remote secret. Stop monitoring service.")
                stopSelf()
                return@launch
            }

            launch {
                unlockRemoteSecret()
            }
            launch {
                monitorRemoteSecret()
            }.invokeOnCompletion {
                logger.warn("Monitoring was stopped")
            }
        }
    }

    private suspend fun unlockRemoteSecret() = coroutineScope {
        while (isActive) {
            if (masterKeyManager.isLockedWithRemoteSecret()) {
                try {
                    appStartupMonitor.whileFetchingRemoteSecret {
                        masterKeyManager.unlockWithRemoteSecret()
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to unlock with remote secret", e)
                    appStartupMonitor.reportUnexpectedAppStartupError("RS-UNLOCK")
                    stopSelf()
                }
            }

            masterKeyManager.masterKeyProvider.awaitLocked()
        }
    }

    private suspend fun monitorRemoteSecret() = coroutineScope {
        while (isActive) {
            try {
                masterKeyManager.monitorRemoteSecret()
            } catch (_: BlockedByAdminException) {
                logger.info("User is blocked by admin")
                masterKeyManager.lockPermanently()
                appStartupMonitor.reportAppStartupError(AppStartupError.BlockedByAdmin)
            } catch (e: RemoteSecretMonitorException) {
                logger.warn("Fetching/monitoring remote secret failed", e)
                masterKeyManager.lockWithRemoteSecret()
                appStartupMonitor.reportAppStartupError(AppStartupError.FailedToFetchRemoteSecret)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Fetching/monitoring remote secret failed unexpectedly", e)
                masterKeyManager.lockPermanently()
                appStartupMonitor.reportAppStartupError(AppStartupError.Unexpected("RS-MONITOR"))
            }

            // Wait for the user to request a retry
            remoteSecretMonitorRetryController.awaitRetryRequest()
            appStartupMonitor.clearTemporaryStartupErrors()
        }
    }

    override fun onDestroy() {
        logger.info("Remote secret service is being destroyed")
        super.onDestroy()
        if (masterKeyManager.isProtectedWithRemoteSecret() == true) {
            masterKeyManager.lockWithRemoteSecret()
            appRestarter.showRestartNotification()
        }
    }

    class Scheduler(
        private val appContext: Context,
        private val appRestarter: AppRestarter,
    ) {
        fun start() {
            logger.debug("start")
            try {
                val serviceIntent = Intent(appContext, RemoteSecretMonitorService::class.java)
                ContextCompat.startForegroundService(appContext, serviceIntent)
            } catch (e: IllegalStateException) {
                logger.error("Could not start remote secret monitoring service", e)
                appRestarter.showRestartNotification()
            }
        }

        fun stop() {
            logger.debug("stop")
            val serviceIntent = Intent(appContext, RemoteSecretMonitorService::class.java)
            appContext.stopService(serviceIntent)
        }
    }

    companion object {
        private val FG_SERVICE_TYPE =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
    }
}
