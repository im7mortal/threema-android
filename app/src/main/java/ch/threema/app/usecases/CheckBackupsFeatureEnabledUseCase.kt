package ch.threema.app.usecases

import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig

class CheckBackupsFeatureEnabledUseCase(
    private val appRestrictions: AppRestrictions,
    private val threemaSafeMDMConfig: ThreemaSafeMDMConfig,
) {
    fun call(): Boolean {
        if (appRestrictions.isBackupsDisabled()) {
            return false
        }
        if (appRestrictions.isDataBackupsDisabled() && threemaSafeMDMConfig.isBackupDisabled) {
            return false
        }
        return true
    }
}
