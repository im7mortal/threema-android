package ch.threema.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import ch.threema.android.showToast
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

class DatabaseDowngradeHelperImpl(
    private val appContext: Context,
    private val sharedPreferences: SharedPreferences,
) : DatabaseDowngradeHelper {
    fun forceEnableDowngrade() {
        isDatabaseDowngradeEnabled = true
        appContext.showToast("Force-downgrade enabled. Re-open the app.")
        exitProcess(0)
    }

    private var isDatabaseDowngradeEnabled: Boolean
        get() {
            val forceDowngradeEnabledAt = sharedPreferences.getLong(FORCE_DOWNGRADE_KEY, 0)
            return forceDowngradeEnabledAt > System.currentTimeMillis() - DOWNGRADE_TIMEOUT.inWholeMilliseconds
        }
        set(value) {
            sharedPreferences.edit(commit = true) {
                if (value) {
                    putLong(FORCE_DOWNGRADE_KEY, System.currentTimeMillis())
                } else {
                    remove(FORCE_DOWNGRADE_KEY)
                }
            }
        }

    @Throws(DatabaseDowngradeException::class)
    override fun onDowngrade(oldVersion: Int) {
        if (isDatabaseDowngradeEnabled) {
            isDatabaseDowngradeEnabled = false
        } else {
            throw DatabaseDowngradeException(oldVersion)
        }
    }

    companion object {
        private val DOWNGRADE_TIMEOUT = 30.seconds
        private const val FORCE_DOWNGRADE_KEY = "_dev_force_db_downgrade"
    }
}
