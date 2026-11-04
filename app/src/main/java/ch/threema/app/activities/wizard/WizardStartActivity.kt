package ch.threema.app.activities.wizard

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.services.UserService
import ch.threema.app.services.notification.NotificationService
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class WizardStartActivity : ThreemaAppCompatActivity() {

    private val userService: UserService by inject()
    private val notificationService: NotificationService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO(ANDR-4389): Improve app readiness check
        lifecycleScope.launch {
            awaitAppFullyReady()
            notificationService.cancelRestoreCompletionNotification()
            launchNextActivity()
        }
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
