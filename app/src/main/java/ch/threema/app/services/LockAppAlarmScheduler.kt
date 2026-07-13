package ch.threema.app.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.getSystemService
import ch.threema.app.receivers.AlarmManagerBroadcastReceiver
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
        val lockingIntent = Intent(appContext, AlarmManagerBroadcastReceiver::class.java)
        lockingIntent.putExtra(LifetimeServiceImpl.REQUEST_CODE_KEY, LifetimeServiceImpl.REQUEST_LOCK_APP)
        val intent = PendingIntent.getBroadcast(
            appContext,
            LifetimeServiceImpl.REQUEST_LOCK_APP,
            lockingIntent,
            IntentDataUtil.PENDING_INTENT_FLAG_MUTABLE,
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
