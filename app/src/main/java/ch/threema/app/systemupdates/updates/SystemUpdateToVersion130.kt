package ch.threema.app.systemupdates.updates

import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.types.Identity
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion130")

/**
 * Goes through all uses of [EncryptedPreferenceStore.getStringArray] and:
 * - checks if their value can be decrypted, and if not deletes the value. This is to clean up any failed previous data migration or
 *   otherwise corrupted data
 * - checks if a legacy value exists in the unencrypted preference store and moves it over
 * - if the value represents a list of identities, filters out any invalid identities
 */
class SystemUpdateToVersion130 : SystemUpdate {

    private val preferenceStore: PreferenceStore by inject()
    private val encryptedPreferenceStore: EncryptedPreferenceStore by inject()

    override fun run() {
        listOf(
            "identity_list_blacklist",
            "identity_list_sync_excluded",
            "identity_list_profilepics",
            "recent_uids",
        )
            .forEach(::migrateOrDelete)

        listOf(
            "identity_list_blacklist",
            "identity_list_sync_excluded",
            "identity_list_profilepics",
        )
            .forEach(::sanitizeIdentities)
    }

    private fun migrateOrDelete(listName: String) {
        try {
            val value = encryptedPreferenceStore.getStringArray(listName)
            if (value != null) {
                preferenceStore.remove(listName)
                return
            }

            // check if we have an old unencrypted value - migrate if necessary
            if (preferenceStore.containsKey(listName)) {
                val legacyValue = preferenceStore.getStringArray(listName)
                if (legacyValue != null) {
                    encryptedPreferenceStore.save(listName, legacyValue)
                }
                preferenceStore.remove(listName)

                // Call the getter to sanity check that the new value can be decrypted
                encryptedPreferenceStore.getStringArray(listName)
            }
        } catch (e: Exception) {
            logger.error("Failed to migrate {}, deleting", listName, e)
            preferenceStore.remove(listName)
            encryptedPreferenceStore.remove(listName)
        }
    }

    private fun sanitizeIdentities(listName: String) {
        val value = encryptedPreferenceStore.getStringArray(listName)
            ?: return
        if (value.all(Identity::isValidIdentity)) {
            return
        }
        encryptedPreferenceStore.save(listName, value.filter(Identity::isValidIdentity).toTypedArray())
    }

    override val version = 130
}
