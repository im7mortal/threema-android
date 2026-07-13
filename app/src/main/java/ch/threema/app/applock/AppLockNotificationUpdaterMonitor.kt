package ch.threema.app.applock

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.monitors.Monitor
import ch.threema.app.services.LockAppService
import ch.threema.app.services.notification.NotificationService
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import org.koin.core.component.KoinComponent

class AppLockNotificationUpdaterMonitor(
    private val lockAppService: LockAppService,
) : Monitor("AppLockNotificationUpdaterMonitor"), KoinComponent {
    private val notificationService: NotificationService? by injectNullableNonBinding()

    override suspend fun run() {
        lockAppService.watchLockState()
            .dropWhile { state ->
                state != LockAppService.LockState.LOCKED
            }
            .filter { state ->
                state == LockAppService.LockState.UNLOCKED
            }
            .collect {
                onAppUnlocked()
            }
    }

    private fun onAppUnlocked() {
        notificationService?.cancelAppLockedNewMessagesNotification()
    }
}
