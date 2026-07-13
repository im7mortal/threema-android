package ch.threema.app.restrictions

import androidx.annotation.AnyThread
import ch.threema.app.routines.UpdateWorkInfoRoutine
import ch.threema.app.services.license.LicenseServiceUser
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.IdentityProvider

private val logger = getThreemaLogger("WorkInfoUpdater")

class WorkInfoUpdater(
    private val identityProvider: IdentityProvider,
) {
    @AnyThread
    fun updateWorkInfo(licenseService: LicenseServiceUser) {
        if (!identityProvider.hasIdentity()) {
            return
        }
        logger.info("Schedule work info update")
        Thread {
            val error = licenseService.validate(false)
            if (error == null) {
                UpdateWorkInfoRoutine.create()?.run()
            } else {
                logger.info("Credentials could not be validated, do not update work info: {}", error)
            }
        }.start()
    }
}
