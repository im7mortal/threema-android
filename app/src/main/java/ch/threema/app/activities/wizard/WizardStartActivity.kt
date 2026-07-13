package ch.threema.app.activities.wizard

import android.content.Context
import android.os.Bundle
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.services.UserService
import ch.threema.app.services.notification.NotificationService
import org.koin.android.ext.android.inject

class WizardStartActivity : ThreemaAppCompatActivity() {

    private val userService: UserService by inject()
    private val notificationService: NotificationService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationService.cancelRestoreCompletionNotification()
        launchNextActivity()
    }

    private fun launchNextActivity() {
        val intent = if (userService.hasIdentity()) {
            WizardBaseActivity.createIntent(this)
        } else {
            WizardIntroActivity.createIntent(this)
        }

        startActivity(intent)
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out)
        finish()
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<WizardStartActivity>(context)
    }
}
