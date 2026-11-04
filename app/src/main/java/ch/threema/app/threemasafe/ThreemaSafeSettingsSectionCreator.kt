package ch.threema.app.threemasafe

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ExcludedSyncIdentitiesService

class ThreemaSafeSettingsSectionCreator(
    private val blockedIdentitiesService: BlockedIdentitiesService,
    private val excludedSyncIdentitiesService: ExcludedSyncIdentitiesService,
    private val preferenceService: PreferenceService,
    private val synchronizedSettingsService: SynchronizedSettingsService,
) {
    fun create(): ThreemaSafeSettingsSection = ThreemaSafeSettingsSection.create(
        settings = ThreemaSafeSettingsSection.ThreemaSafeSettings(
            syncContacts = synchronizedSettingsService.isSyncContacts(),
            blockUnknown = synchronizedSettingsService.isBlockUnknown(),
            readReceipts = synchronizedSettingsService.areReadReceiptsEnabled(),
            sendTyping = synchronizedSettingsService.isTypingIndicatorEnabled(),
            threemaCalls = synchronizedSettingsService.isVoipEnabled(),
            relayThreemaCalls = synchronizedSettingsService.isForceTURN(),
            disableScreenshots = synchronizedSettingsService.areScreenshotsDisabled(),
            incognitoKeyboard = synchronizedSettingsService.isIncognitoKeyboardRequested(),
            blockedContacts = blockedIdentitiesService.getAllBlockedIdentities(),
            syncExcludedIds = excludedSyncIdentitiesService.getAllExcludedIdentities(),
            recentEmojis = preferenceService.getRecentEmojis().toSet(),
        ),
    )
}
