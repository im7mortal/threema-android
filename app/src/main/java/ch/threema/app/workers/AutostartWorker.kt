package ch.threema.app.workers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ch.threema.android.buildNotification
import ch.threema.android.checkGrantedPermission
import ch.threema.android.getPendingIntentForActivity
import ch.threema.app.R
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.home.HomeActivity
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.UserService
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.MasterKeyManager
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("AutostartWorker")

class AutostartWorker(
    val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val masterKeyManager: MasterKeyManager by inject()
    private val preferenceService: PreferenceService by inject()
    private val synchronizedSettingsService: SynchronizedSettingsService by inject()
    private val userService: UserService by inject()

    override suspend fun doWork(): Result {
        logger.info("Processing AutoStart - start")

        if (
            masterKeyManager.isLockedWithPassphrase() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || context.checkGrantedPermission(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            val notification = buildNotification(context, NotificationChannels.NOTIFICATION_CHANNEL_NOTICE) {
                setSmallIcon(R.drawable.ic_notification_small)
                setContentTitle(context.getString(R.string.master_key_locked))
                setContentText(context.getString(R.string.master_key_locked_notify_description))
                setTicker(context.getString(R.string.master_key_locked))
                setCategory(NotificationCompat.CATEGORY_SERVICE)

                val notificationIntent = IntentDataUtil.createActionIntentHideAfterUnlock(HomeActivity.createIntent(context))
                notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                setContentIntent(
                    getPendingIntentForActivity(
                        context = context,
                        requestCode = NotificationRequestCodes.HOME_ACTIVITY,
                        intent = notificationIntent,
                        flags = PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            NotificationManagerCompat.from(context).notify(
                NotificationIDs.MASTER_KEY_LOCKED_NOTIFICATION_ID,
                notification,
            )
        }

        awaitAppFullyReadyWithTimeout(timeout = 20.seconds)
            ?: return Result.retry()

        // reset feature level
        preferenceService.setTransmittedFeatureMask(0)

        // auto fix failed sync account
        if (synchronizedSettingsService.isSyncContacts()) {
            if (!userService.checkAccount()) {
                // create account
                userService.getAccount(true)
                userService.enableAccountAutoSync(true)
            }
        }

        logger.info("Processing AutoStart - end")
        return Result.success()
    }
}
