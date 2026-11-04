package ch.threema.app.restrictions

import android.os.Bundle
import androidx.annotation.WorkerThread
import ch.threema.app.work.MdmSource
import ch.threema.domain.protocol.api.work.WorkMDMSettings

interface AppRestrictionService {
    val appRestrictions: Bundle?

    /**
     * The mdm parameter source. Contains the information which types of restrictions are set.
     */
    val mdmSource: MdmSource

    /**
     * Save the given WorkMDMSettings and reload the AppRestrictions
     */
    @WorkerThread
    fun storeWorkMDMSettings(settings: WorkMDMSettings)

    /**
     * Reload restrictions (without fetching work data)
     */
    @WorkerThread
    fun reload()
}
