package ch.threema.app.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import androidx.core.content.getSystemService
import ch.threema.android.getPendingIntentForBroadcast
import ch.threema.app.applock.LockAppBroadcastReceiver
import ch.threema.app.utils.IntentDataUtil
import kotlin.time.Duration

class LockAppAlarmScheduler(
    private val appContext: Context,
) {
    private val alarmManager: AlarmManager?
        get() = appContext.getSystemService<AlarmManager>()

    private var lockTimerIntent: PendingIntent? = null

    fun startTimer(delay: Duration) {
        cancelTimer()
        val lockingIntent = LockAppBroadcastReceiver.createIntent(appContext)
        val intent = getPendingIntentForBroadcast(
            context = appContext,
            requestCode = 0,
            intent = lockingIntent,
            flags = IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE,
        )
        alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay.inWholeMilliseconds, intent)
        this.lockTimerIntent = intent
    }

    fun cancelTimer() {
        lockTimerIntent?.let { intent ->
            alarmManager?.cancel(intent)
        }
    }
}
