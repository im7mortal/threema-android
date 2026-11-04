package ch.threema.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ch.threema.android.DeviceInfo
import ch.threema.android.buildNotification
import ch.threema.android.getPendingIntentForActivity
import ch.threema.app.R
import ch.threema.app.activities.CloseAppActivity
import ch.threema.app.activities.ThreemaPushNotificationInfoActivity
import ch.threema.app.managers.ServiceManager
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs.THREEMA_PUSH_ACTIVE_NOTIFICATION_ID
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.getThreemaLogger
import org.slf4j.Logger

private val logger = getThreemaLogger("ThreemaPushService")

class ThreemaPushService : Service() {
    private var lifetimeService: LifetimeService? = null

    @Synchronized
    override fun onCreate() {
        logger.debug("onCreate")
        super.onCreate()

        // Create intent triggered by notification
        val intent = ThreemaPushNotificationInfoActivity.createIntent(this)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val contentIntent = getPendingIntentForActivity(
            context = this,
            requestCode = NotificationRequestCodes.THREEMA_PUSH,
            intent = intent,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        createNotificationChannel()
        val notification = buildNotification(this, NotificationChannels.NOTIFICATION_CHANNEL_THREEMA_PUSH) {
            setContentTitle(getString(R.string.threema_push))
            setContentText(getString(R.string.threema_push_notification_text))
            setSmallIcon(R.drawable.ic_notification_push)
            setLocalOnly(true)
            setContentIntent(contentIntent)
        }
        ServiceCompat.startForeground(
            this,
            THREEMA_PUSH_ACTIVE_NOTIFICATION_ID,
            notification,
            FG_SERVICE_TYPE,
        )
        logger.info("startForeground called")

        // Get lifetime service
        //
        // Initialization may lock the app for a while, so we display the above notification
        // *before* getting the service to avoid a "Context.startForegroundService() did not
        // then call Service.startForeground()" exception.
        val serviceManager = ServiceManager.get()
        if (serviceManager == null) {
            logger.error("Service Manager not available (passphrase locked?). Can't start Threema Push.")
            stopSelf()
            return
        }
        val lifetimeService = serviceManager.lifetimeService
        this.lifetimeService = lifetimeService

        // Acquire unpauseable connection while the service is running
        lifetimeService.acquireUnpauseableConnection(LIFETIME_SERVICE_TAG)
        isRunning = true
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @Synchronized
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.trace("onStartCommand")
        if (intent == null || intent.action == null) {
            return START_NOT_STICKY
        }
        if (isStopping) {
            // Already stopping
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> logger.info("ACTION_START")
            ACTION_STOP -> {
                logger.info("ACTION_STOP")
                isRunning = false
                isStopping = true
                stopSelf()
            }

            else -> {
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        logger.trace("onDestroy")

        // Release connection
        lifetimeService?.releaseConnection(LIFETIME_SERVICE_TAG)

        // Remove notification
        removeNotification()

        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        logger.info("stopForeground")

        // Done
        isRunning = false
        super.onDestroy()
        isStopping = false
        logger.info("Service destroyed")
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        logger.info("onLowMemory")
        super.onLowMemory()
    }

    override fun onTimeout(startId: Int) {
        logger.warn("onTimeout called, this is going to kill the foreground service :(")
        super.onTimeout(startId)
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        logger.info("onTaskRemoved")
        CloseAppActivity.closeApp(this)
    }

    /**
     * Remove the persistent notification.
     */
    private fun removeNotification() {
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        notificationManagerCompat.cancel(THREEMA_PUSH_ACTIVE_NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (!DeviceInfo.supportsNotificationChannels()) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(this)
        val notificationChannel = NotificationChannel(
            NotificationChannels.NOTIFICATION_CHANNEL_THREEMA_PUSH,
            getString(R.string.threema_push),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationChannel.description = getString(R.string.threema_push_service_description)
        notificationChannel.enableLights(false)
        notificationChannel.enableVibration(false)
        notificationChannel.setShowBadge(false)
        notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationChannel.setSound(null, null)

        notificationManager.createNotificationChannel(notificationChannel)
    }

    class Starter(
        private val appContext: Context,
        private val preferenceService: PreferenceService,
    ) {
        fun startIfNeeded(callerLogger: Logger): Boolean {
            if (preferenceService.mustUseThreemaPush()) {
                if (!isRunning) {
                    val intent = Intent(appContext, ThreemaPushService::class.java)
                        .setAction(ACTION_START)
                    callerLogger.info("Starting ThreemaPushService")
                    try {
                        ContextCompat.startForegroundService(appContext, intent)
                        return true
                    } catch (e: Exception) {
                        callerLogger.error("Unable to start foreground service", e)
                    }
                }
            }
            return false
        }

        fun stop() {
            appContext.stopService(
                Intent(appContext, ThreemaPushService::class.java),
            )
        }
    }

    companion object {
        private const val LIFETIME_SERVICE_TAG = "threemaPushService"
        private val FG_SERVICE_TYPE =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0

        // Intent actions
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"

        // State variables
        var isRunning = false
            private set
        private var isStopping = false
    }
}
