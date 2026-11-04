package ch.threema.app.systemupdates.updates

import ch.threema.app.stores.PreferenceStore
import org.koin.core.component.inject

/**
 * remove group link settings
 */
class SystemUpdateToVersion111 : SystemUpdate {

    private val preferenceStore: PreferenceStore by inject()

    override fun run() {
        preferenceStore.remove("pref_group_request_overview_hidden")
    }

    override val version = 111
}
