package ch.threema.app.restrictions

import android.os.Bundle
import androidx.annotation.WorkerThread
import ch.threema.domain.protocol.api.work.WorkMDMSettings

interface AppRestrictionService {
    val appRestrictions: Bundle?

    /**
     * Get the source of active mdm parameters in text representation.
     *
     * If at least one Threema-MDM parameter and at least one external MDM parameter is active, "me" is returned.
     * If at least one Threema-MDM parameter is active, append "m" is returned.
     * If at least one external MDM parameter is active, append "e" is returned.
     *
     * (See "Update Work Info" in documentation)
     *
     * @return the source(s) of active mdm parameters as text, null if no mdm parameters are active
     */
    val mdmSource: String?

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
