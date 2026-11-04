package ch.threema.app.threemasafe

import ch.threema.base.utils.getThreemaLogger
import kotlinx.serialization.json.JsonObject

private val logger = getThreemaLogger("ThreemaSafeBackupCreator")

class ThreemaSafeBackupCreator(
    private val infoSectionCreator: ThreemaSafeInfoSectionCreator,
    private val userSectionCreator: ThreemaSafeUserSectionCreator,
    private val contactsSectionCreator: ThreemaSafeContactsSectionCreator,
    private val groupsSectionCreator: ThreemaSafeGroupsSectionCreator,
    private val distributionListsSectionCreator: ThreemaSafeDistributionListsSectionCreator,
    private val settingsSectionCreator: ThreemaSafeSettingsSectionCreator,
) {
    /**
     * Generate and return the Threema Safe JSON.
     * <p>
     * If an error occurs, the error is logged and `null` is returned.
     */
    fun createThreemaSafeBackup(): JsonObject? = try {
        val threemaSafeBackup = ThreemaSafeBackup.create(
            threemaSafeBackupSections = ThreemaSafeBackup.ThreemaSafeBackupSections(
                infoSection = infoSectionCreator.create(),
                userSection = userSectionCreator.create(),
                contactsSection = contactsSectionCreator.create(),
                groupsSection = groupsSectionCreator.create(),
                distributionListsSection = distributionListsSectionCreator.create(),
                settingsSection = settingsSectionCreator.create(),
            ),
        )

        threemaSafeBackup.getJsonObject()
    } catch (e: Exception) {
        logger.error("Could not create threema safe backup", e)
        null
    }
}
