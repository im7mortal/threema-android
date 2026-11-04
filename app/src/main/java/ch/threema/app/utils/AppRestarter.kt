package ch.threema.app.utils

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import ch.threema.android.buildNotification
import ch.threema.android.checkGrantedPermission
import ch.threema.android.getPendingIntentForActivity
import ch.threema.app.R
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.app.workers.AppRestartWorker
import ch.threema.base.utils.getThreemaLogger
import kotlin.time.Duration.Companion.milliseconds

private val logger = getThreemaLogger("AppRestarter")

class AppRestarter(
    private val appContext: Context,
    private val appRestartWorkerScheduler: AppRestartWorker.Scheduler,
) {
    /**
     * Android Q and newer does not allow restart in the background:
     * [Restrictions on starting activities from the background](https://developer.android.com/preview/privacy/background-activity-starts)
     */
    @JvmOverloads
    fun scheduleAppRestart(delayMs: Long = 0) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val manager = appContext.getSystemService<AlarmManager>()!!
            manager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delayMs, getRestartPendingIntent())
        } else if (appContext.checkGrantedPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            showRestartNotification()
        } else {
            appRestartWorkerScheduler.schedule(delayMs.milliseconds)
        }
    }

    fun showRestartNotification() {
        if (!appContext.checkGrantedPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            logger.warn("Cannot show restart notification because notification permission is not granted")
            return
        }
        val notification = getRestartNotification()
        val notificationManagerCompat = NotificationManagerCompat.from(appContext)
        notificationManagerCompat.notify(NotificationIDs.APP_RESTART_NOTIFICATION_ID, notification)
    }

    private fun getRestartNotification(): Notification =
        buildNotification(appContext, NotificationChannels.NOTIFICATION_CHANNEL_ALERT) {
            val text = appContext.getString(R.string.tap_to_start, appContext.getString(R.string.app_name))
            setSmallIcon(R.drawable.ic_notification_small)
            setContentTitle(appContext.getString(R.string.notification_title_restart))
            setContentText(text)
            setDefaults(Notification.DEFAULT_LIGHTS or Notification.DEFAULT_VIBRATE)
            setPriority(NotificationCompat.PRIORITY_MAX)
            setStyle(NotificationCompat.BigTextStyle().bigText(text))
            setContentIntent(getRestartPendingIntent())
            setAutoCancel(false)
        }

    private fun getRestartPendingIntent(): PendingIntent {
        val restartIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)!!
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return getPendingIntentForActivity(
            context = appContext,
            requestCode = NotificationRequestCodes.APP_RESTART,
            intent = restartIntent,
            flags = PendingIntent.FLAG_CANCEL_CURRENT or IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE,
        )
    }
}
