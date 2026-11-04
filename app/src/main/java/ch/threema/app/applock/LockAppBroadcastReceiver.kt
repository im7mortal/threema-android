package ch.threema.app.applock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.threema.android.buildBroadcastReceiverIntent
import ch.threema.android.goAsync
import ch.threema.app.services.LockAppService
import ch.threema.base.utils.getThreemaLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("LockAppBroadcastReceiver")

class LockAppBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val lockAppService: LockAppService by inject()

    override fun onReceive(context: Context, intent: Intent) = goAsync {
        if (!lockAppService.isLockingEnabled || lockAppService.isLocked) {
            return@goAsync
        }
        logger.info("Locking app")
        try {
            lockAppService.lock()
            logger.info("App locked")
        } catch (e: Exception) {
            logger.error("Failed to lock app", e)
        }
    }

    companion object {
        fun createIntent(context: Context) = buildBroadcastReceiverIntent<LockAppBroadcastReceiver>(context)
    }
}
