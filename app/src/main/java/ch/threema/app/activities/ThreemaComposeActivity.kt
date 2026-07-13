package ch.threema.app.activities

import android.content.Intent
import android.os.Bundle
import androidx.annotation.CallSuper
import ch.threema.app.activities.wizard.WizardIntroActivity
import ch.threema.app.di.DIJavaCompat.isSessionScopeReady
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.startup.AppStartupAware
import ch.threema.app.utils.ConfigUtils
import org.koin.android.ext.android.inject

abstract class ThreemaComposeActivity : ThreemaActivity() {

    private val notificationPreferenceService: NotificationPreferenceService by inject()
    private val synchronizedSettingsService: SynchronizedSettingsService by inject()
    private val lockAppService: LockAppService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The license can not be checked if the session scope is not ready, so we potentially skip the check here.
        // This isn't ideal, but at the latest in the next ThreemaToolbarActivity, another check will be made.
        if (isSessionScopeReady() && ConfigUtils.isSerialLicensed() && !ConfigUtils.isSerialLicenseValid()) {
            startActivity(Intent(this, EnterSerialActivity::class.java))
            finish()
            return
        }

        // TODO(ANDR-4389): Improve app-startup behavior
        if (this !is AppStartupAware && !initActivity(savedInstanceState)) {
            finish()
        }
    }

    /**
     * It is called from onCreate() after all the basic initialization has been done.
     * Override this to do your own initialization.
     *
     * @param savedInstanceState the bundle provided to onCreate()
     * @return true on success, false otherwise, in which case the activity is finished
     */
    @CallSuper
    protected open fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!isSessionScopeReady()) {
            return false
        }

        if (notificationPreferenceService.getWizardRunning()) {
            startActivity(WizardIntroActivity.createIntent(this))
            return false
        }

        ConfigUtils.applyScreenshotPolicy(
            /* activity = */
            this,
            /* synchronizedSettingsService = */
            synchronizedSettingsService,
            /* lockAppService = */
            lockAppService,
        )

        return true
    }
}
