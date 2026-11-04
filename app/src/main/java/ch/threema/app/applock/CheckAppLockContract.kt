package ch.threema.app.applock

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import ch.threema.app.pinlock.PinLockActivity
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.LockAppService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * @param promptIfUnlocked If true, the user will be prompted to unlock the app even if they have already done so recently, which may be useful
 *   for authentication for sensitive operations. If false, the user will only be prompted to unlock if the app is currently locked.
 */
class CheckAppLockContract
@JvmOverloads
constructor(
    private val promptIfUnlocked: Boolean = true,
) : ActivityResultContract<Unit, Boolean>(), KoinComponent {

    private val lockAppService: LockAppService by inject()
    private val preferenceService: PreferenceService by inject()

    /**
     * If no lock mechanism is configured, this will return a [SynchronousResult] of `true`,
     * indicating that the app is already unlocked and that no activity should be started for unlocking.
     * The same will happen if [promptIfUnlocked] is `false` and the app is already (temporarily) unlocked.
     * Otherwise, [createIntent] gets called afterward.
     */
    override fun getSynchronousResult(context: Context, input: Unit): SynchronousResult<Boolean>? =
        when (preferenceService.getLockMechanism()) {
            PreferenceService.LockMechanism.SYSTEM,
            PreferenceService.LockMechanism.BIOMETRIC,
            PreferenceService.LockMechanism.PIN,
            -> if (!promptIfUnlocked && !lockAppService.isLocked) {
                SynchronousResult(true)
            } else {
                null
            }
            PreferenceService.LockMechanism.NONE -> SynchronousResult(true)
        }

    override fun createIntent(context: Context, input: Unit): Intent =
        when (val lockMechanism = preferenceService.getLockMechanism()) {
            PreferenceService.LockMechanism.SYSTEM,
            PreferenceService.LockMechanism.BIOMETRIC,
            -> {
                AppLockActivity.createIntent(context, checkOnly = true)
            }
            PreferenceService.LockMechanism.PIN -> {
                PinLockActivity.createIntent(context, checkOnly = true)
            }
            PreferenceService.LockMechanism.NONE -> error("Unexpected lock mechanism: $lockMechanism")
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}
