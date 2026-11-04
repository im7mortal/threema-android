package ch.threema.app.threemasafe

import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class ThreemaSafeBackupCreatorTest {

    private val infoSectionCreatorMock = mockk<ThreemaSafeInfoSectionCreator>()
    private val userSectionCreatorMock = mockk<ThreemaSafeUserSectionCreator>()
    private val contactsSectionCreatorMock = mockk<ThreemaSafeContactsSectionCreator>()
    private val groupsSectionCreatorMock = mockk<ThreemaSafeGroupsSectionCreator>()
    private val distributionListsSectionCreatorMock = mockk<ThreemaSafeDistributionListsSectionCreator>()
    private val settingsSectionCreatorMock = mockk<ThreemaSafeSettingsSectionCreator>()

    private fun creator() = ThreemaSafeBackupCreator(
        infoSectionCreator = infoSectionCreatorMock,
        userSectionCreator = userSectionCreatorMock,
        contactsSectionCreator = contactsSectionCreatorMock,
        groupsSectionCreator = groupsSectionCreatorMock,
        distributionListsSectionCreator = distributionListsSectionCreatorMock,
        settingsSectionCreator = settingsSectionCreatorMock,
    )

    private fun setupSections() {
        every { infoSectionCreatorMock.create() } returns ThreemaSafeInfoSection.create(
            version = 1,
            device = "1.2.3A/de_DE",
        )
        every { userSectionCreatorMock.create() } returns ThreemaSafeUserSection.create(
            ThreemaSafeUserSection.ThreemaSafeUserInfo(
                privateKey = "A".repeat(43) + "=",
                nickname = "nickname",
                profilePic = null,
                profilePicRelease = ThreemaSafeUserSection.ProfilePicRelease.None,
                links = emptyList(),
            ),
        )
        every { contactsSectionCreatorMock.create() } returns ThreemaSafeContactsSection.create(emptyList())
        every { groupsSectionCreatorMock.create() } returns ThreemaSafeGroupsSection.create(emptyList())
        every { distributionListsSectionCreatorMock.create() } returns ThreemaSafeDistributionListsSection.create(emptyList())
        every { settingsSectionCreatorMock.create() } returns ThreemaSafeSettingsSection.create(
            ThreemaSafeSettingsSection.ThreemaSafeSettings(
                syncContacts = true,
                blockUnknown = true,
                readReceipts = true,
                sendTyping = true,
                threemaCalls = true,
                relayThreemaCalls = true,
                disableScreenshots = true,
                incognitoKeyboard = true,
                blockedContacts = emptySet(),
                syncExcludedIds = emptySet(),
                recentEmojis = emptySet(),
            ),
        )
    }

    @Test
    fun `createThreemaSafeBackup should produce the expected json structure`() {
        setupSections()

        val jsonObject = creator().createThreemaSafeBackup()

        val expectedJson = """
            {
                "info": {"version":1,"device":"1.2.3A/de_DE"},
                "user": {"privatekey":"${"A".repeat(43)}=","nickname":"nickname","profilePicRelease":[null],"links":[]},
                "contacts": [],
                "groups": [],
                "distributionlists": [],
                "settings": {"syncContacts":true,"blockUnknown":true,"readReceipts":true,"sendTyping":true,"threemaCalls":true,"relayThreemaCalls":true,"disableScreenshots":true,"incognitoKeyboard":true,"blockedContacts":[],"syncExcludedIds":[],"recentEmojis":[]}
            }
        """.trimIndent()

        assertNotNull(jsonObject)
        assertEquals(Json.parseToJsonElement(expectedJson), jsonObject)
    }

    @Test
    fun `createThreemaSafeBackup should return null when a section creator throws`() {
        setupSections()
        every { userSectionCreatorMock.create() } throws RuntimeException("boom")

        val jsonObject = creator().createThreemaSafeBackup()

        assertNull(jsonObject)
    }
}
