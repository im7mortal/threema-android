package ch.threema.app.services

import android.app.Activity
import android.content.Context
import ch.threema.android.disableEnterTransition
import ch.threema.app.applock.AppLockActivity
import ch.threema.app.pinlock.PinLockActivity
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.localcrypto.MasterKeyProvider
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ActivityService")

class ActivityService(
    private val appContext: Context,
    private val lockAppService: LockAppService,
    private val preferenceService: PreferenceService,
    private val masterKeyProvider: MasterKeyProvider,
    private val dispatcherProvider: DispatcherProvider,
) {
    private var currentActivityReference = WeakReference<Activity>(null)

    private val coroutineScope = CoroutineScope(dispatcherProvider.main.immediate)

    init {
        coroutineScope.launch {
            lockAppService.watchLockState()
                .dropWhile { state ->
                    state == LockAppService.LockState.UNLOCKED
                }
                .collect { state ->
                    logger.info("App lock state = {}", state)
                    if (state == LockAppService.LockState.LOCKED) {
                        askUserToUnlockIfPossible()
                    }
                }
        }
    }

    private suspend fun askUserToUnlockIfPossible(): Unit = withContext(dispatcherProvider.main.immediate) {
        val currentActivity = currentActivityReference.get()
        if (masterKeyProvider.isLocked() || currentActivity == null || currentActivity is PinLockActivity || currentActivity is AppLockActivity) {
            // Don't ask the user to unlock when the app is not in the foreground or is already showing the unlock activity
            return@withContext
        }

        val lockMechanism = preferenceService.getLockMechanism()
        currentActivityReference.get()?.let { activity ->
            val intent = when (lockMechanism) {
                PreferenceService.LockMechanism.SYSTEM,
                PreferenceService.LockMechanism.BIOMETRIC,
                -> AppLockActivity.createIntent(activity)
                else -> PinLockActivity.createIntent(appContext)
            }
            activity.startActivity(intent)
            activity.disableEnterTransition()
        }
    }

    fun resume(currentActivity: Activity?) {
        currentActivityReference = WeakReference(currentActivity)
        lockAppService.lockIfExpired()

        if (lockAppService.isLockingEnabled) {
            if (!lockAppService.isLocked) {
                lockAppService.onUserActivity()
            } else {
                coroutineScope.launch {
                    askUserToUnlockIfPossible()
                }
            }
        }
    }

    fun pause(pausedActivity: Activity?) {
        if (currentActivityReference.get() === pausedActivity) {
            currentActivityReference.clear()
        }
    }

    fun destroy(destroyedActivity: Activity?) {
        if (currentActivityReference.get() === destroyedActivity) {
            currentActivityReference.clear()
        }
    }

    fun userInteract(interactedActivity: Activity?) {
        if (currentActivityReference.get() !== interactedActivity) {
            currentActivityReference.clear()
            currentActivityReference = WeakReference(interactedActivity)
        }
        if (lockAppService.isLockingEnabled && !lockAppService.isLocked) {
            lockAppService.onUserActivity()
        }
    }

    companion object : KoinComponent {
        private val activityService: ActivityService by inject()

        @Deprecated("Use instance of ActivityService directly")
        @JvmStatic
        fun activityUserInteract(interactedActivity: Activity?) {
            activityService.userInteract(interactedActivity)
        }
    }
}
