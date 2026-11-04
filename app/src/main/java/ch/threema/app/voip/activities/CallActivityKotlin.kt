package ch.threema.app.voip.activities

import android.content.Intent
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.applock.CheckAppLockContract

abstract class CallActivityKotlin : ThreemaActivity() {

    private val checkLockLauncher = registerForActivityResult(CheckAppLockContract(promptIfUnlocked = false)) { unlocked ->
        if (unlocked) {
            initializeCall(intent)
        } else {
            finish()
        }
    }

    protected fun initializeCallWithAppLockCheck() {
        // On incoming calls we bypass the app lock, for better UX
        if (intent.getBooleanExtra(CallActivity.EXTRA_ACCEPT_INCOMING_CALL, false)) {
            initializeCall(intent)
        } else {
            checkLockLauncher.launch(Unit)
        }
    }

    protected abstract fun initializeCall(intent: Intent)
}
