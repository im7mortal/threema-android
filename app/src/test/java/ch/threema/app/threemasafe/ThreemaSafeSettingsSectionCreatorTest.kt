package ch.threema.app.threemasafe

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.common.booleanOrNull
import ch.threema.common.string
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import testdata.TestData

class ThreemaSafeSettingsSectionCreatorTest {

    private val blockedIdentitiesServiceMock = mockk<BlockedIdentitiesService>()
    private val excludedSyncIdentitiesServiceMock = mockk<ExcludedSyncIdentitiesService>()
    private val preferenceServiceMock = mockk<PreferenceService>()
    private val synchronizedSettingsServiceMock = mockk<SynchronizedSettingsService>()

    private fun creator() = ThreemaSafeSettingsSectionCreator(
        blockedIdentitiesService = blockedIdentitiesServiceMock,
        excludedSyncIdentitiesService = excludedSyncIdentitiesServiceMock,
        preferenceService = preferenceServiceMock,
        synchronizedSettingsService = synchronizedSettingsServiceMock,
    )

    private fun setupDefaults() {
        every { synchronizedSettingsServiceMock.isSyncContacts() } returns true
        every { synchronizedSettingsServiceMock.isBlockUnknown() } returns true
        every { synchronizedSettingsServiceMock.areReadReceiptsEnabled() } returns true
        every { synchronizedSettingsServiceMock.isTypingIndicatorEnabled() } returns true
        every { synchronizedSettingsServiceMock.isVoipEnabled() } returns true
        every { synchronizedSettingsServiceMock.isForceTURN() } returns true
        every { synchronizedSettingsServiceMock.areScreenshotsDisabled() } returns true
        every { synchronizedSettingsServiceMock.isIncognitoKeyboardRequested() } returns true
        every { blockedIdentitiesServiceMock.getAllBlockedIdentities() } returns setOf(TestData.Identities.OTHER_1.value)
        every { excludedSyncIdentitiesServiceMock.getAllExcludedIdentities() } returns setOf(TestData.Identities.OTHER_2.value)
        every { preferenceServiceMock.getRecentEmojis() } returns listOf("😀")
    }

    @Test
    fun `create should map sync contacts true`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isSyncContacts() } returns true

        val jsonObject = creator().create().getJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeSettingsSection.TAG_SYNC_CONTACTS]?.booleanOrNull)
    }

    @Test
    fun `create should map sync contacts false`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isSyncContacts() } returns false

        val jsonObject = creator().create().getJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeSettingsSection.TAG_SYNC_CONTACTS]?.booleanOrNull)
    }

    @Test
    fun `create should map block unknown true`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isBlockUnknown() } returns true

        val jsonObject = creator().create().getJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeSettingsSection.TAG_BLOCK_UNKNOWN]?.booleanOrNull)
    }

    @Test
    fun `create should map block unknown false`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isBlockUnknown() } returns false

        val jsonObject = creator().create().getJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeSettingsSection.TAG_BLOCK_UNKNOWN]?.booleanOrNull)
    }

    @Test
    fun `create should map read receipts true`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.areReadReceiptsEnabled() } returns true

        val jsonObject = creator().create().getJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeSettingsSection.TAG_READ_RECEIPTS]?.booleanOrNull)
    }

    @Test
    fun `create should map read receipts false`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.areReadReceiptsEnabled() } returns false

        val jsonObject = creator().create().getJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeSettingsSection.TAG_READ_RECEIPTS]?.booleanOrNull)
    }

    @Test
    fun `create should map send typing true`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isTypingIndicatorEnabled() } returns true

        val jsonObject = creator().create().getJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeSettingsSection.TAG_SEND_TYPING]?.booleanOrNull)
    }

    @Test
    fun `create should map send typing false`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isTypingIndicatorEnabled() } returns false

        val jsonObject = creator().create().getJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeSettingsSection.TAG_SEND_TYPING]?.booleanOrNull)
    }

    @Test
    fun `create should map threema calls true`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isVoipEnabled() } returns true

        val jsonObject = creator().create().getJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeSettingsSection.TAG_THREEMA_CALLS]?.booleanOrNull)
    }

    @Test
    fun `create should map threema calls false`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isVoipEnabled() } returns false

        val jsonObject = creator().create().getJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeSettingsSection.TAG_THREEMA_CALLS]?.booleanOrNull)
    }

    @Test
    fun `create should map relay threema calls true`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isForceTURN() } returns true

        val jsonObject = creator().create().getJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeSettingsSection.TAG_RELAY_THREEMA_CALLS]?.booleanOrNull)
    }

    @Test
    fun `create should map relay threema calls false`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isForceTURN() } returns false

        val jsonObject = creator().create().getJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeSettingsSection.TAG_RELAY_THREEMA_CALLS]?.booleanOrNull)
    }

    @Test
    fun `create should map disable screenshots true`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.areScreenshotsDisabled() } returns true

        val jsonObject = creator().create().getJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeSettingsSection.TAG_DISABLE_SCREENSHOTS]?.booleanOrNull)
    }

    @Test
    fun `create should map disable screenshots false`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.areScreenshotsDisabled() } returns false

        val jsonObject = creator().create().getJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeSettingsSection.TAG_DISABLE_SCREENSHOTS]?.booleanOrNull)
    }

    @Test
    fun `create should map incognito keyboard true`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isIncognitoKeyboardRequested() } returns true

        val jsonObject = creator().create().getJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeSettingsSection.TAG_INCOGNITO_KEYBOARD]?.booleanOrNull)
    }

    @Test
    fun `create should map incognito keyboard false`() {
        setupDefaults()
        every { synchronizedSettingsServiceMock.isIncognitoKeyboardRequested() } returns false

        val jsonObject = creator().create().getJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeSettingsSection.TAG_INCOGNITO_KEYBOARD]?.booleanOrNull)
    }

    @Test
    fun `create should map blocked contacts from blocked identities service`() {
        setupDefaults()
        every { blockedIdentitiesServiceMock.getAllBlockedIdentities() } returns setOf(
            TestData.Identities.OTHER_1.value,
            TestData.Identities.OTHER_2.value,
        )

        val jsonObject = creator().create().getJsonObject()

        val values = jsonObject[ThreemaSafeSettingsSection.TAG_BLOCKED_CONTACTS]?.jsonArray?.map(JsonElement::string)
        assertEquals(setOf(TestData.Identities.OTHER_1.value, TestData.Identities.OTHER_2.value), values?.toSet())
    }

    @Test
    fun `create should map sync excluded ids from excluded sync identities service`() {
        setupDefaults()
        every { excludedSyncIdentitiesServiceMock.getAllExcludedIdentities() } returns setOf(
            TestData.Identities.OTHER_1.value,
            TestData.Identities.OTHER_2.value,
        )

        val jsonObject = creator().create().getJsonObject()

        val values = jsonObject[ThreemaSafeSettingsSection.TAG_SYNC_EXCLUDED_IDS]?.jsonArray?.map(JsonElement::string)
        assertEquals(setOf(TestData.Identities.OTHER_1.value, TestData.Identities.OTHER_2.value), values?.toSet())
    }

    @Test
    fun `create should map recent emojis as a set from preference service`() {
        setupDefaults()
        every { preferenceServiceMock.getRecentEmojis() } returns listOf("😀", "😀", "😎")

        val jsonObject = creator().create().getJsonObject()

        val values = jsonObject[ThreemaSafeSettingsSection.TAG_RECENT_EMOJIS]?.jsonArray?.map(JsonElement::string)
        assertEquals(setOf("😀", "😎"), values?.toSet())
    }
}
