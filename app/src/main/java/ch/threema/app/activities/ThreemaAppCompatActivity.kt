package ch.threema.app.activities

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import ch.threema.app.GlobalAppState
import ch.threema.app.GlobalAppState.SpecialMode
import ch.threema.app.backupcenter.CreateLocalDataBackupActivity
import ch.threema.app.backuprestore.libthreema.BackupRestoreProgressActivity as CrossPlatformBackupRestoreProgressActivity
import ch.threema.app.services.avatarcache.AvatarCacheService
import ch.threema.app.utils.ConfigUtils
import org.koin.android.ext.android.inject

abstract class ThreemaAppCompatActivity : AppCompatActivity() {
    private val globalAppState: GlobalAppState by inject()
    private val avatarCacheService: AvatarCacheService by inject()

    private var savedDayNightMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        this.enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (finishAndStartSpecialModeScreenIfNeeded()) {
            return
        }

        savedDayNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        ConfigUtils.setCurrentDayNightMode(
            if (savedDayNightMode == Configuration.UI_MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
        )

        // Enable the on back pressed callback if the activity uses custom back navigation
        if (this is BackPressHandler) {
            onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        this@ThreemaAppCompatActivity.handleOnBackPressed()
                    }
                },
            )
        }
    }

    override fun onResume() {
        finishAndStartSpecialModeScreenIfNeeded()
        try {
            super.onResume()
        } catch (_: IllegalArgumentException) {
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val newDayNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (savedDayNightMode != newDayNightMode) {
            savedDayNightMode = newDayNightMode
            ConfigUtils.setCurrentDayNightMode(
                if (newDayNightMode == Configuration.UI_MODE_NIGHT_YES) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                },
            )

            // Reset avatar cache on theme change so that the default avatars are loaded with the correct (themed) color
            avatarCacheService.clear()
            recreate()
        }
        super.onConfigurationChanged(newConfig)
    }

    /**
     * Checks if the app is in a special mode, and if so finishes the activity and instead starts the activity appropriate for the mode.
     * @return true if the current activity is finished, requiring that the caller does not perform further initialization, false otherwise
     */
    protected fun finishAndStartSpecialModeScreenIfNeeded(): Boolean {
        if (isFinishing) {
            return true
        }
        when (globalAppState.specialMode) {
            SpecialMode.BACKUP_CREATE,
            SpecialMode.BACKUP_RESTORE,
            -> {
                finish()
                startActivity(BackupRestoreProgressActivity.createIntent(this))
                return true
            }
            SpecialMode.CROSS_PLATFORM_BACKUP_CREATE -> {
                if (this !is CreateLocalDataBackupActivity) {
                    finish()
                    startActivity(CreateLocalDataBackupActivity.createIntent(this))
                    return true
                }
            }
            SpecialMode.CROSS_PLATFORM_BACKUP_RESTORE -> {
                if (this !is CrossPlatformBackupRestoreProgressActivity) {
                    finish()
                    startActivity(CrossPlatformBackupRestoreProgressActivity.createIntent(this))
                    return true
                }
            }
            null -> Unit
        }
        return false
    }
}
