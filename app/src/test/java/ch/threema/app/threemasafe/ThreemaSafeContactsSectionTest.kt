package ch.threema.app.threemasafe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ThreemaSafeContactsSectionTest {

    private fun contact(
        identity: String = "ECHOECHO",
        publicKey: String = "somePublicKey",
        createdAt: Long = 1_000L,
        isRevoked: Boolean = false,
        verificationLevel: ThreemaSafeContactsSection.ThreemaSafeVerificationLevel =
            ThreemaSafeContactsSection.ThreemaSafeVerificationLevel.UNVERIFIED,
        workVerified: Boolean = false,
        hidden: Boolean = false,
        firstname: String = "John",
        lastname: String = "Doe",
        nickname: String = "johnny",
        lastUpdate: Long? = 2_000L,
        private: Boolean = false,
        readReceipts: ThreemaSafeContactsSection.ThreemaSafeSendPolicy =
            ThreemaSafeContactsSection.ThreemaSafeSendPolicy.DEFAULT,
        typingIndicators: ThreemaSafeContactsSection.ThreemaSafeSendPolicy =
            ThreemaSafeContactsSection.ThreemaSafeSendPolicy.DEFAULT,
    ) = ThreemaSafeContactsSection.ThreemaSafeContact(
        identity = identity,
        publicKey = publicKey,
        createdAt = createdAt,
        isRevoked = isRevoked,
        verificationLevel = verificationLevel,
        workVerified = workVerified,
        hidden = hidden,
        firstname = firstname,
        lastname = lastname,
        nickname = nickname,
        lastUpdate = lastUpdate,
        private = private,
        readReceipts = readReceipts,
        typingIndicators = typingIndicators,
    )

    private fun jsonObjectOf(contacts: ThreemaSafeContactsSection.ThreemaSafeContact) =
        ThreemaSafeContactsSection.create(listOf(contacts)).getJsonArray()[0] as JsonObject

    @Test
    fun `create should include identity in json object`() {
        val jsonObject = jsonObjectOf(contact(identity = "ECHOECHO"))

        assertEquals(JsonPrimitive("ECHOECHO"), jsonObject[ThreemaSafeContactsSection.TAG_IDENTITY])
    }

    @Test
    fun `create should include public key when contact is revoked`() {
        val jsonObject = jsonObjectOf(
            contact(
                publicKey = "revokedKey",
                isRevoked = true,
                verificationLevel = ThreemaSafeContactsSection.ThreemaSafeVerificationLevel.UNVERIFIED,
            ),
        )

        assertEquals(JsonPrimitive("revokedKey"), jsonObject[ThreemaSafeContactsSection.TAG_PUBLIC_KEY])
    }

    @Test
    fun `create should include public key when contact is fully verified`() {
        val jsonObject = jsonObjectOf(
            contact(
                publicKey = "verifiedKey",
                isRevoked = false,
                verificationLevel = ThreemaSafeContactsSection.ThreemaSafeVerificationLevel.FULLY_VERIFIED,
            ),
        )

        assertEquals(JsonPrimitive("verifiedKey"), jsonObject[ThreemaSafeContactsSection.TAG_PUBLIC_KEY])
    }

    @Test
    fun `create should not include public key when unverified and not revoked`() {
        val jsonObject = jsonObjectOf(
            contact(isRevoked = false, verificationLevel = ThreemaSafeContactsSection.ThreemaSafeVerificationLevel.UNVERIFIED),
        )

        assertEquals(false, jsonObject.containsKey(ThreemaSafeContactsSection.TAG_PUBLIC_KEY))
    }

    @Test
    fun `create should not include public key when server verified and not revoked`() {
        val jsonObject = jsonObjectOf(
            contact(isRevoked = false, verificationLevel = ThreemaSafeContactsSection.ThreemaSafeVerificationLevel.SERVER_VERIFIED),
        )

        assertEquals(false, jsonObject.containsKey(ThreemaSafeContactsSection.TAG_PUBLIC_KEY))
    }

    @Test
    fun `create should include created at in json object`() {
        val jsonObject = jsonObjectOf(contact(createdAt = 1234L))

        assertEquals(JsonPrimitive(1234L), jsonObject[ThreemaSafeContactsSection.TAG_CREATED_AT])
    }

    @Test
    fun `create should include verification level value in json object`() {
        val jsonObject = jsonObjectOf(
            contact(verificationLevel = ThreemaSafeContactsSection.ThreemaSafeVerificationLevel.SERVER_VERIFIED),
        )

        assertEquals(JsonPrimitive(1), jsonObject[ThreemaSafeContactsSection.TAG_VERIFICATION_LEVEL])
    }

    @Test
    fun `create should include work verified in json object`() {
        val jsonObject = jsonObjectOf(contact(workVerified = true))

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeContactsSection.TAG_WORK_VERIFIED])
    }

    @Test
    fun `create should include hidden in json object`() {
        val jsonObject = jsonObjectOf(contact(hidden = true))

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeContactsSection.TAG_HIDDEN])
    }

    @Test
    fun `create should include firstname in json object`() {
        val jsonObject = jsonObjectOf(contact(firstname = "Jane"))

        assertEquals(JsonPrimitive("Jane"), jsonObject[ThreemaSafeContactsSection.TAG_FIRST_NAME])
    }

    @Test
    fun `create should include lastname in json object`() {
        val jsonObject = jsonObjectOf(contact(lastname = "Smith"))

        assertEquals(JsonPrimitive("Smith"), jsonObject[ThreemaSafeContactsSection.TAG_LAST_NAME])
    }

    @Test
    fun `create should include nickname in json object`() {
        val jsonObject = jsonObjectOf(contact(nickname = "janey"))

        assertEquals(JsonPrimitive("janey"), jsonObject[ThreemaSafeContactsSection.TAG_NICKNAME])
    }

    @Test
    fun `create should include last update when present`() {
        val jsonObject = jsonObjectOf(contact(lastUpdate = 5_000L))

        assertEquals(JsonPrimitive(5_000L), jsonObject[ThreemaSafeContactsSection.TAG_LAST_UPDATE])
    }

    @Test
    fun `create should not include last update key when null`() {
        val jsonObject = jsonObjectOf(contact(lastUpdate = null))

        assertEquals(false, jsonObject.containsKey(ThreemaSafeContactsSection.TAG_LAST_UPDATE))
    }

    @Test
    fun `create should include private (true) in json object`() {
        val jsonObject = jsonObjectOf(contact(private = true))

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeContactsSection.TAG_PRIVATE])
    }

    @Test
    fun `create should include private (false) in json object`() {
        val jsonObject = jsonObjectOf(contact(private = false))

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeContactsSection.TAG_PRIVATE])
    }

    @Test
    fun `create should include read receipts (default) value in json object`() {
        val jsonObject = jsonObjectOf(
            contact(readReceipts = ThreemaSafeContactsSection.ThreemaSafeSendPolicy.DEFAULT),
        )

        assertEquals(JsonPrimitive(0), jsonObject[ThreemaSafeContactsSection.TAG_READ_RECEIPTS])
    }

    @Test
    fun `create should include read receipts (send) value in json object`() {
        val jsonObject = jsonObjectOf(
            contact(readReceipts = ThreemaSafeContactsSection.ThreemaSafeSendPolicy.SEND),
        )

        assertEquals(JsonPrimitive(1), jsonObject[ThreemaSafeContactsSection.TAG_READ_RECEIPTS])
    }

    @Test
    fun `create should include read receipts (don't send) value in json object`() {
        val jsonObject = jsonObjectOf(
            contact(readReceipts = ThreemaSafeContactsSection.ThreemaSafeSendPolicy.DONT_SEND),
        )

        assertEquals(JsonPrimitive(2), jsonObject[ThreemaSafeContactsSection.TAG_READ_RECEIPTS])
    }

    @Test
    fun `create should include typing indicators (default) value in json object`() {
        val jsonObject = jsonObjectOf(
            contact(typingIndicators = ThreemaSafeContactsSection.ThreemaSafeSendPolicy.DEFAULT),
        )

        assertEquals(JsonPrimitive(0), jsonObject[ThreemaSafeContactsSection.TAG_TYPING_INDICATORS])
    }

    @Test
    fun `create should include typing indicators (send) value in json object`() {
        val jsonObject = jsonObjectOf(
            contact(typingIndicators = ThreemaSafeContactsSection.ThreemaSafeSendPolicy.SEND),
        )

        assertEquals(JsonPrimitive(1), jsonObject[ThreemaSafeContactsSection.TAG_TYPING_INDICATORS])
    }

    @Test
    fun `create should include typing indicators (don't send) value in json object`() {
        val jsonObject = jsonObjectOf(
            contact(typingIndicators = ThreemaSafeContactsSection.ThreemaSafeSendPolicy.DONT_SEND),
        )

        assertEquals(JsonPrimitive(2), jsonObject[ThreemaSafeContactsSection.TAG_TYPING_INDICATORS])
    }

    @Test
    fun `create should only contain the expected keys when last update is null and public key is omitted`() {
        val jsonObject = jsonObjectOf(
            contact(
                lastUpdate = null,
                isRevoked = false,
                verificationLevel = ThreemaSafeContactsSection.ThreemaSafeVerificationLevel.UNVERIFIED,
            ),
        )

        assertEquals(
            setOf(
                ThreemaSafeContactsSection.TAG_IDENTITY,
                ThreemaSafeContactsSection.TAG_CREATED_AT,
                ThreemaSafeContactsSection.TAG_VERIFICATION_LEVEL,
                ThreemaSafeContactsSection.TAG_WORK_VERIFIED,
                ThreemaSafeContactsSection.TAG_HIDDEN,
                ThreemaSafeContactsSection.TAG_FIRST_NAME,
                ThreemaSafeContactsSection.TAG_LAST_NAME,
                ThreemaSafeContactsSection.TAG_NICKNAME,
                ThreemaSafeContactsSection.TAG_TYPING_INDICATORS,
                ThreemaSafeContactsSection.TAG_READ_RECEIPTS,
                ThreemaSafeContactsSection.TAG_PRIVATE,
            ),
            jsonObject.keys,
        )
    }

    @Test
    fun `create should produce one entry per contact`() {
        val section = ThreemaSafeContactsSection.create(
            listOf(contact(identity = "ECHOECHO"), contact(identity = "01234567")),
        )

        val jsonArray = section.getJsonArray()

        assertEquals(2, jsonArray.size)
    }

    @Test
    fun `ThreemaSafeContact should throw if identity is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            contact(identity = "invalid")
        }
    }

    @Test
    fun `ThreemaSafeContact should throw if created at is negative`() {
        assertFailsWith<IllegalArgumentException> {
            contact(createdAt = -1L)
        }
    }

    @Test
    fun `ThreemaSafeContact should throw if last update is negative`() {
        assertFailsWith<IllegalArgumentException> {
            contact(lastUpdate = -1L)
        }
    }
}
