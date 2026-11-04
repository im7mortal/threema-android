package ch.threema.app.restrictions

import android.os.Bundle
import ch.threema.app.work.MdmSource
import ch.threema.domain.protocol.api.work.WorkMDMSettings

@Suppress("unused")
class NoOpAppRestrictionService : AppRestrictionService {
    override val appRestrictions: Bundle?
        get() = null

    override val mdmSource = MdmSource(
        hasThreemaMdmRestrictions = false,
        hasExternalMdmRestrictions = false,
    )

    override fun storeWorkMDMSettings(settings: WorkMDMSettings) {
        // do nothing
    }

    override fun reload() {
        // do nothing
    }
}
