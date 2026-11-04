package ch.threema.app.threemasafe

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class ThreemaSafeBackup private constructor(private val jsonObject: JsonObject) {
    fun getJsonObject(): JsonObject = jsonObject

    data class ThreemaSafeBackupSections(
        val infoSection: ThreemaSafeInfoSection,
        val userSection: ThreemaSafeUserSection,
        val contactsSection: ThreemaSafeContactsSection,
        val groupsSection: ThreemaSafeGroupsSection,
        val distributionListsSection: ThreemaSafeDistributionListsSection,
        val settingsSection: ThreemaSafeSettingsSection,
    )

    companion object {
        const val SECTION_INFO = "info"
        const val SECTION_USER = "user"
        const val SECTION_CONTACTS = "contacts"
        const val SECTION_GROUPS = "groups"
        const val SECTION_DISTRIBUTION_LISTS = "distributionlists"
        const val SECTION_SETTINGS = "settings"

        fun create(threemaSafeBackupSections: ThreemaSafeBackupSections): ThreemaSafeBackup {
            val jsonObject = buildJsonObject {
                put(SECTION_INFO, threemaSafeBackupSections.infoSection.getJsonObject())
                put(SECTION_USER, threemaSafeBackupSections.userSection.getJsonObject())
                put(SECTION_CONTACTS, threemaSafeBackupSections.contactsSection.getJsonArray())
                put(SECTION_GROUPS, threemaSafeBackupSections.groupsSection.getJsonArray())
                put(SECTION_DISTRIBUTION_LISTS, threemaSafeBackupSections.distributionListsSection.getJsonArray())
                put(SECTION_SETTINGS, threemaSafeBackupSections.settingsSection.getJsonObject())
            }
            return ThreemaSafeBackup(jsonObject)
        }
    }
}
