package ch.threema.app.threemasafe

import ch.threema.common.toJsonArray
import ch.threema.domain.types.Identity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ThreemaSafeContactsSection private constructor(private val jsonArray: JsonArray) {
    fun getJsonArray(): JsonArray = jsonArray

    enum class ThreemaSafeVerificationLevel(val value: Int) {
        UNVERIFIED(0),
        SERVER_VERIFIED(1),
        FULLY_VERIFIED(2),
    }

    enum class ThreemaSafeSendPolicy(val value: Int) {
        DEFAULT(0),
        SEND(1),
        DONT_SEND(2),
    }

    data class ThreemaSafeContact(
        val identity: String,
        val publicKey: String,
        val createdAt: Long,
        val isRevoked: Boolean,
        val verificationLevel: ThreemaSafeVerificationLevel,
        val workVerified: Boolean,
        val hidden: Boolean,
        val firstname: String,
        val lastname: String,
        val nickname: String,
        val lastUpdate: Long?,
        val private: Boolean,
        val readReceipts: ThreemaSafeSendPolicy,
        val typingIndicators: ThreemaSafeSendPolicy,
    ) {
        init {
            require(Identity.isValidIdentity(identity)) {
                "invalid identity"
            }
            require(createdAt >= 0) {
                "negative created at timestamp"
            }
            require((lastUpdate ?: 0) >= 0) {
                "negative last update timestamp"
            }
        }
    }

    companion object {
        const val TAG_IDENTITY = "identity"
        const val TAG_PUBLIC_KEY = "publickey"
        const val TAG_CREATED_AT = "createdAt"
        const val TAG_VERIFICATION_LEVEL = "verification"
        const val TAG_WORK_VERIFIED = "workVerified"
        const val TAG_HIDDEN = "hidden"
        const val TAG_FIRST_NAME = "firstname"
        const val TAG_LAST_NAME = "lastname"
        const val TAG_NICKNAME = "nickname"
        const val TAG_LAST_UPDATE = "lastUpdate"
        const val TAG_PRIVATE = "private"
        const val TAG_READ_RECEIPTS = "readReceipts"
        const val TAG_TYPING_INDICATORS = "typingIndicators"

        fun create(contacts: List<ThreemaSafeContact>): ThreemaSafeContactsSection =
            ThreemaSafeContactsSection(
                contacts.map(::toJsonObject).toJsonArray(),
            )

        private fun toJsonObject(contact: ThreemaSafeContact): JsonObject = buildJsonObject {
            put(TAG_IDENTITY, contact.identity)
            // Back up the public key if the contact is verified, or if it's revoked.
            //
            // Rationale: If the contact is unverified, then it doesn't really matter and the
            // public key can be re-fetched. By not including the key in the backup, we can reduce
            // its size. On the other hand, if a contact is revoked, then the public key cannot be
            // re-fetched, so it must be included.
            if (contact.isRevoked || contact.verificationLevel == ThreemaSafeVerificationLevel.FULLY_VERIFIED) {
                put(TAG_PUBLIC_KEY, contact.publicKey)
            }
            put(TAG_CREATED_AT, contact.createdAt)
            put(TAG_VERIFICATION_LEVEL, contact.verificationLevel.value)
            put(TAG_WORK_VERIFIED, contact.workVerified)
            put(TAG_HIDDEN, contact.hidden)
            put(TAG_FIRST_NAME, contact.firstname)
            put(TAG_LAST_NAME, contact.lastname)
            put(TAG_NICKNAME, contact.nickname)
            contact.lastUpdate?.let { lastUpdate ->
                put(TAG_LAST_UPDATE, lastUpdate)
            }
            put(TAG_TYPING_INDICATORS, contact.typingIndicators.value)
            put(TAG_READ_RECEIPTS, contact.readReceipts.value)
            put(TAG_PRIVATE, contact.private)
        }
    }
}
