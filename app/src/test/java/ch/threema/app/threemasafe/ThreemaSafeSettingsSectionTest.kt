package ch.threema.app.threemasafe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class ThreemaSafeSettingsSectionTest {

    private fun settings(
        syncContacts: Boolean = true,
        blockUnknown: Boolean = true,
        readReceipts: Boolean = true,
        sendTyping: Boolean = true,
        threemaCalls: Boolean = true,
        relayThreemaCalls: Boolean = true,
        disableScreenshots: Boolean = true,
        incognitoKeyboard: Boolean = true,
        blockedContacts: Set<String> = setOf("76543210"),
        syncExcludedIds: Set<String> = setOf("01234567"),
        recentEmojis: Set<String> = setOf("😀"),
    ) = ThreemaSafeSettingsSection.ThreemaSafeSettings(
        syncContacts = syncContacts,
        blockUnknown = blockUnknown,
        readReceipts = readReceipts,
        sendTyping = sendTyping,
        threemaCalls = threemaCalls,
        relayThreemaCalls = relayThreemaCalls,
        disableScreenshots = disableScreenshots,
        incognitoKeyboard = incognitoKeyboard,
        blockedContacts = blockedContacts,
        syncExcludedIds = syncExcludedIds,
        recentEmojis = recentEmojis,
    )

    @Test
    fun `create should include sync contacts true in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(syncContacts = true)).getJsonObject()

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeSettingsSection.TAG_SYNC_CONTACTS])
    }

    @Test
    fun `create should include sync contacts false in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(syncContacts = false)).getJsonObject()

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeSettingsSection.TAG_SYNC_CONTACTS])
    }

    @Test
    fun `create should include block unknown true in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(blockUnknown = true)).getJsonObject()

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeSettingsSection.TAG_BLOCK_UNKNOWN])
    }

    @Test
    fun `create should include block unknown false in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(blockUnknown = false)).getJsonObject()

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeSettingsSection.TAG_BLOCK_UNKNOWN])
    }

    @Test
    fun `create should include read receipts true in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(readReceipts = true)).getJsonObject()

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeSettingsSection.TAG_READ_RECEIPTS])
    }

    @Test
    fun `create should include read receipts false in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(readReceipts = false)).getJsonObject()

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeSettingsSection.TAG_READ_RECEIPTS])
    }

    @Test
    fun `create should include send typing true in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(sendTyping = true)).getJsonObject()

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeSettingsSection.TAG_SEND_TYPING])
    }

    @Test
    fun `create should include send typing false in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(sendTyping = false)).getJsonObject()

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeSettingsSection.TAG_SEND_TYPING])
    }

    @Test
    fun `create should include threema calls true in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(threemaCalls = true)).getJsonObject()

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeSettingsSection.TAG_THREEMA_CALLS])
    }

    @Test
    fun `create should include threema calls false in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(threemaCalls = false)).getJsonObject()

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeSettingsSection.TAG_THREEMA_CALLS])
    }

    @Test
    fun `create should include relay threema calls true in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(relayThreemaCalls = true)).getJsonObject()

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeSettingsSection.TAG_RELAY_THREEMA_CALLS])
    }

    @Test
    fun `create should include relay threema calls false in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(relayThreemaCalls = false)).getJsonObject()

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeSettingsSection.TAG_RELAY_THREEMA_CALLS])
    }

    @Test
    fun `create should include disable screenshots true in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(disableScreenshots = true)).getJsonObject()

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeSettingsSection.TAG_DISABLE_SCREENSHOTS])
    }

    @Test
    fun `create should include disable screenshots false in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(disableScreenshots = false)).getJsonObject()

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeSettingsSection.TAG_DISABLE_SCREENSHOTS])
    }

    @Test
    fun `create should include incognito keyboard true in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(incognitoKeyboard = true)).getJsonObject()

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeSettingsSection.TAG_INCOGNITO_KEYBOARD])
    }

    @Test
    fun `create should include incognito keyboard false in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(incognitoKeyboard = false)).getJsonObject()

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeSettingsSection.TAG_INCOGNITO_KEYBOARD])
    }

    @Test
    fun `create should include blocked contacts in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(blockedContacts = setOf("76543210"))).getJsonObject()

        assertEquals(
            JsonArray(listOf(JsonPrimitive("76543210"))),
            jsonObject[ThreemaSafeSettingsSection.TAG_BLOCKED_CONTACTS],
        )
    }

    @Test
    fun `create should include sync excluded ids in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(syncExcludedIds = setOf("01234567"))).getJsonObject()

        assertEquals(
            JsonArray(listOf(JsonPrimitive("01234567"))),
            jsonObject[ThreemaSafeSettingsSection.TAG_SYNC_EXCLUDED_IDS],
        )
    }

    @Test
    fun `create should include recent emojis in json object`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings(recentEmojis = setOf("😀"))).getJsonObject()

        assertEquals(
            JsonArray(listOf(JsonPrimitive("😀"))),
            jsonObject[ThreemaSafeSettingsSection.TAG_RECENT_EMOJIS],
        )
    }

    @Test
    fun `create should include empty arrays for empty sets`() {
        val jsonObject = ThreemaSafeSettingsSection.create(
            settings(blockedContacts = emptySet(), syncExcludedIds = emptySet(), recentEmojis = emptySet()),
        ).getJsonObject()

        assertEquals(JsonArray(emptyList()), jsonObject[ThreemaSafeSettingsSection.TAG_BLOCKED_CONTACTS])
        assertEquals(JsonArray(emptyList()), jsonObject[ThreemaSafeSettingsSection.TAG_SYNC_EXCLUDED_IDS])
        assertEquals(JsonArray(emptyList()), jsonObject[ThreemaSafeSettingsSection.TAG_RECENT_EMOJIS])
    }

    @Test
    fun `create should only contain the expected keys`() {
        val jsonObject = ThreemaSafeSettingsSection.create(settings()).getJsonObject()

        assertEquals(
            setOf(
                ThreemaSafeSettingsSection.TAG_SYNC_CONTACTS,
                ThreemaSafeSettingsSection.TAG_BLOCK_UNKNOWN,
                ThreemaSafeSettingsSection.TAG_READ_RECEIPTS,
                ThreemaSafeSettingsSection.TAG_SEND_TYPING,
                ThreemaSafeSettingsSection.TAG_THREEMA_CALLS,
                ThreemaSafeSettingsSection.TAG_RELAY_THREEMA_CALLS,
                ThreemaSafeSettingsSection.TAG_DISABLE_SCREENSHOTS,
                ThreemaSafeSettingsSection.TAG_INCOGNITO_KEYBOARD,
                ThreemaSafeSettingsSection.TAG_BLOCKED_CONTACTS,
                ThreemaSafeSettingsSection.TAG_SYNC_EXCLUDED_IDS,
                ThreemaSafeSettingsSection.TAG_RECENT_EMOJIS,
            ),
            jsonObject.keys,
        )
    }

    @Test
    fun `ThreemaSafeSettings should throw if a blocked contact identity is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            settings(blockedContacts = setOf("invalid"))
        }
    }

    @Test
    fun `ThreemaSafeSettings should throw if a sync excluded id is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            settings(syncExcludedIds = setOf("invalid"))
        }
    }

    @Test
    fun `ThreemaSafeSettings should throw if a recent emoji is blank`() {
        assertFailsWith<IllegalArgumentException> {
            settings(recentEmojis = setOf(" "))
        }
    }
}
