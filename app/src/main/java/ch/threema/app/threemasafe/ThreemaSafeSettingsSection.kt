package ch.threema.app.threemasafe

import ch.threema.domain.types.Identity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ThreemaSafeSettingsSection private constructor(private val jsonObject: JsonObject) {
    fun getJsonObject(): JsonObject = jsonObject

    data class ThreemaSafeSettings(
        val syncContacts: Boolean,
        val blockUnknown: Boolean,
        val readReceipts: Boolean,
        val sendTyping: Boolean,
        val threemaCalls: Boolean,
        val relayThreemaCalls: Boolean,
        val disableScreenshots: Boolean,
        val incognitoKeyboard: Boolean,
        val blockedContacts: Set<String>,
        val syncExcludedIds: Set<String>,
        val recentEmojis: Set<String>,
    ) {
        init {
            require(blockedContacts.all(Identity::isValidIdentity)) {
                "invalid blocked contact identities"
            }
            require(syncExcludedIds.all(Identity::isValidIdentity)) {
                "invalid sync excluded identities"
            }
            require(recentEmojis.all { emoji -> emoji.isNotBlank() }) {
                "invalid recent emoji"
            }
        }
    }

    companion object {
        const val TAG_SYNC_CONTACTS = "syncContacts"
        const val TAG_BLOCK_UNKNOWN = "blockUnknown"
        const val TAG_READ_RECEIPTS = "readReceipts"
        const val TAG_SEND_TYPING = "sendTyping"
        const val TAG_THREEMA_CALLS = "threemaCalls"
        const val TAG_RELAY_THREEMA_CALLS = "relayThreemaCalls"
        const val TAG_DISABLE_SCREENSHOTS = "disableScreenshots"
        const val TAG_INCOGNITO_KEYBOARD = "incognitoKeyboard"
        const val TAG_BLOCKED_CONTACTS = "blockedContacts"
        const val TAG_SYNC_EXCLUDED_IDS = "syncExcludedIds"
        const val TAG_RECENT_EMOJIS = "recentEmojis"

        fun create(settings: ThreemaSafeSettings): ThreemaSafeSettingsSection {
            val jsonObject = buildJsonObject {
                put(TAG_SYNC_CONTACTS, settings.syncContacts)
                put(TAG_BLOCK_UNKNOWN, settings.blockUnknown)
                put(TAG_READ_RECEIPTS, settings.readReceipts)
                put(TAG_SEND_TYPING, settings.sendTyping)
                put(TAG_THREEMA_CALLS, settings.threemaCalls)
                put(TAG_RELAY_THREEMA_CALLS, settings.relayThreemaCalls)
                put(TAG_DISABLE_SCREENSHOTS, settings.disableScreenshots)
                put(TAG_INCOGNITO_KEYBOARD, settings.incognitoKeyboard)
                put(TAG_BLOCKED_CONTACTS, JsonArray(settings.blockedContacts.map(::JsonPrimitive)))
                put(TAG_SYNC_EXCLUDED_IDS, JsonArray(settings.syncExcludedIds.map(::JsonPrimitive)))
                put(TAG_RECENT_EMOJIS, JsonArray(settings.recentEmojis.map(::JsonPrimitive)))
            }
            return ThreemaSafeSettingsSection(jsonObject)
        }
    }
}
