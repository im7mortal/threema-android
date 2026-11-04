package ch.threema.app.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import ch.threema.android.buildNotification
import ch.threema.android.getPendingIntentForActivity
import ch.threema.app.R
import ch.threema.app.activities.CloseAppActivity
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.home.HomeActivity
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.app.passphrase.PassphraseLockActivity
import ch.threema.app.services.notification.NotificationService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.logging.logAndReportError
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("PassphraseService")

class PassphraseService : Service() {

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        try {
            showPersistentNotification()
        } catch (e: Exception) {
            logger.logAndReportError("Failed to start service", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        removePersistentNotification(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceIntent = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        logger.info("*** PassphraseService task removed")
        CloseAppActivity.closeApp(this)
    }

    private fun showPersistentNotification() {
        val context = this
        val notification = buildNotification(context, channelId = NotificationChannels.NOTIFICATION_CHANNEL_PASSPHRASE) {
            setSmallIcon(R.drawable.ic_passphrase_unlocked)
            setContentTitle(getString(R.string.app_name))
            setContentText(getString(R.string.masterkey_is_unlocked))
            setPriority(NotificationCompat.PRIORITY_MIN)
            addAction(
                R.drawable.ic_lock_filled,
                getString(R.string.title_lock),
                getPendingIntentForActivity(
                    context = context,
                    requestCode = NotificationRequestCodes.LOCK_PASSPHRASE,
                    intent = PassphraseLockActivity.createIntent(context)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                        ),
                    flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            setContentIntent(
                TaskStackBuilder.create(context)
                    .addParentStack(HomeActivity::class.java)
                    .addNextIntent(
                        HomeActivity.createIntent(context)
                            .setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            ),
                    )
                    .getPendingIntent(
                        NotificationRequestCodes.HOME_ACTIVITY,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
            )
        }

        NotificationChannels.createOrMigrateNotificationChannels(context)
        ServiceCompat.startForeground(
            this,
            NotificationIDs.PASSPHRASE_SERVICE_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            },
        )
    }

    class Starter(
        private val appContext: Context,
        private val masterKeyManager: MasterKeyManager,
    ) {
        fun startIfNeeded() {
            if (serviceIntent == null) {
                if (masterKeyManager.isLockedWithPassphrase() || !masterKeyManager.isProtectedWithPassphrase()) {
                    return
                }
                val intent = Intent(appContext, PassphraseService::class.java)
                ContextCompat.startForegroundService(appContext, intent)
                serviceIntent = intent
            } else if (!masterKeyManager.isProtectedWithPassphrase()) {
                stop()
            }
        }

        fun stop() {
            val intent = serviceIntent ?: return
            removePersistentNotification(appContext)
            appContext.stopService(intent)
            serviceIntent = null
        }
    }

    companion object : KoinComponent {
        private val notificationService: NotificationService? by injectNullableNonBinding()

        private var serviceIntent: Intent? = null

        private fun removePersistentNotification(context: Context) {
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            notificationManagerCompat.cancel(NotificationIDs.PASSPHRASE_SERVICE_NOTIFICATION_ID)
            notificationService?.cancelConversationNotificationsOnLockApp()
        }
    }
}
