package ch.threema.app.restrictions

import android.os.Bundle
import ch.threema.domain.protocol.api.work.WorkMDMSettings

class NoOpAppRestrictionService : AppRestrictionService {
    override val appRestrictions: Bundle?
        get() = null

    override val mdmSource: String?
        get() = null

    override fun storeWorkMDMSettings(settings: WorkMDMSettings) {
        // do nothing
    }

    override fun reload() {
        // do nothing
    }
}
