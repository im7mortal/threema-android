package ch.threema.app.services.license

import ch.threema.domain.models.LicenseCredentials

class LicenseServiceNoOp : LicenseService<LicenseCredentials?> {
    override fun validate(credentials: LicenseCredentials?): String? = null

    override fun validate(allowException: Boolean): String? = null

    override fun hasCredentials() = false

    override fun isLicensed() = true

    override fun loadCredentials() = null
}
