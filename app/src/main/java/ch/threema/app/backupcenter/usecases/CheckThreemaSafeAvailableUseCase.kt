package ch.threema.app.backupcenter.usecases

import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig

class CheckThreemaSafeAvailableUseCase(
    private val appRestrictions: AppRestrictions,
    private val threemaSafeMDMConfig: ThreemaSafeMDMConfig,
) {
    fun call(): Boolean =
        !(appRestrictions.isRestricted() && threemaSafeMDMConfig.isBackupAdminDisabled)
}
