package ch.threema.app.threemasafe
import ch.threema.app.services.ContactService
import ch.threema.common.Base64
import ch.threema.common.booleanOrNull
import ch.threema.common.intOrNull
import ch.threema.common.longOrNull
import ch.threema.common.stringOrNull
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.jsonObject
import testdata.TestData

class ThreemaSafeContactsSectionCreatorTest {

    private val contactModelRepositoryMock = mockk<ContactModelRepository>()
    private val contactServiceMock = mockk<ContactService>()

    private fun creator() = ThreemaSafeContactsSectionCreator(
        contactModelRepository = contactModelRepositoryMock,
        contactService = contactServiceMock,
    )

    private fun createContactModelMock(contactModelData: ContactModelData?) = mockk<ContactModel> {
        every { data } returns contactModelData
        every { identity } returns (contactModelData?.identity ?: TestData.Identities.OTHER_1.value)
    }

    private fun setup(
        contactModels: List<ContactModel>,
        removedContacts: Set<String> = emptySet(),
    ) {
        every { contactModelRepositoryMock.getAll() } returns contactModels
        every { contactServiceMock.removedContacts } returns removedContacts
    }

    private fun firstContactJsonObject() =
        creator().create().getJsonArray()[0].jsonObject

    @Test
    fun `create should exclude removed contacts`() {
        val keptContactModelData = TestData.createContactModelData(identity = TestData.Identities.OTHER_1)
        val removedContactModelData = TestData.createContactModelData(identity = TestData.Identities.OTHER_2)
        setup(
            contactModels = listOf(createContactModelMock(keptContactModelData), createContactModelMock(removedContactModelData)),
            removedContacts = setOf(TestData.Identities.OTHER_2.value),
        )

        val section = creator().create()

        assertEquals(1, section.getJsonArray().size)
        assertEquals(
            TestData.Identities.OTHER_1.value,
            section.getJsonArray()[0].jsonObject[ThreemaSafeContactsSection.TAG_IDENTITY]?.stringOrNull,
        )
    }

    @Test
    fun `create should exclude contact models with null data`() {
        setup(
            contactModels = listOf(
                createContactModelMock(null),
                createContactModelMock(TestData.createContactModelData(identity = TestData.Identities.OTHER_1)),
            ),
        )

        val section = creator().create()

        assertEquals(1, section.getJsonArray().size)
    }

    @Test
    fun `create should map identity`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(identity = TestData.Identities.OTHER_1))))

        val jsonObject = firstContactJsonObject()

        assertEquals(
            TestData.Identities.OTHER_1.value,
            jsonObject[ThreemaSafeContactsSection.TAG_IDENTITY]?.stringOrNull,
        )
    }

    @Test
    fun `create should base64 encode the public key`() {
        val publicKeyBytes = ByteArray(32) { it.toByte() }
        setup(listOf(createContactModelMock(TestData.createContactModelData(publicKey = publicKeyBytes))))

        val jsonObject = firstContactJsonObject()

        assertEquals(
            Base64.encode(publicKeyBytes),
            jsonObject[ThreemaSafeContactsSection.TAG_PUBLIC_KEY]?.stringOrNull,
        )
    }

    @Test
    fun `create should map created at to epoch millis`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(createdAt = Instant.ofEpochMilli(12_345L)))))

        val jsonObject = firstContactJsonObject()

        assertEquals(12_345L, jsonObject[ThreemaSafeContactsSection.TAG_CREATED_AT]?.longOrNull)
    }

    @Test
    fun `create should mark contact as revoked when activity state is invalid`() {
        setup(
            listOf(
                createContactModelMock(
                    TestData.createContactModelData(
                        activityState = IdentityState.INVALID,
                        verificationLevel = VerificationLevel.UNVERIFIED,
                    ),
                ),
            ),
        )

        val jsonObject = firstContactJsonObject()

        // Public key must be included when revoked
        assertEquals(true, jsonObject.containsKey(ThreemaSafeContactsSection.TAG_PUBLIC_KEY))
    }

    @Test
    fun `create should map verification level unverified`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(verificationLevel = VerificationLevel.UNVERIFIED))))

        val jsonObject = firstContactJsonObject()

        assertEquals(0, jsonObject[ThreemaSafeContactsSection.TAG_VERIFICATION_LEVEL]?.intOrNull)
    }

    @Test
    fun `create should map verification level server verified`() {
        setup(
            listOf(createContactModelMock(TestData.createContactModelData(verificationLevel = VerificationLevel.SERVER_VERIFIED))),
        )

        val jsonObject = firstContactJsonObject()

        assertEquals(1, jsonObject[ThreemaSafeContactsSection.TAG_VERIFICATION_LEVEL]?.intOrNull)
    }

    @Test
    fun `create should map verification level fully verified`() {
        setup(
            listOf(createContactModelMock(TestData.createContactModelData(verificationLevel = VerificationLevel.FULLY_VERIFIED))),
        )

        val jsonObject = firstContactJsonObject()

        assertEquals(2, jsonObject[ThreemaSafeContactsSection.TAG_VERIFICATION_LEVEL]?.intOrNull)
    }

    @Test
    fun `create should mark work verified true when work verification level is verified`() {
        setup(
            listOf(
                createContactModelMock(
                    TestData.createContactModelData(workVerificationLevel = WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED),
                ),
            ),
        )

        val jsonObject = firstContactJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeContactsSection.TAG_WORK_VERIFIED]?.booleanOrNull)
    }

    @Test
    fun `create should mark work verified false when work verification level is not verified`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(workVerificationLevel = WorkVerificationLevel.NONE))))

        val jsonObject = firstContactJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeContactsSection.TAG_WORK_VERIFIED]?.booleanOrNull)
    }

    @Test
    fun `create should mark hidden false when acquaintance level is direct`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(acquaintanceLevel = AcquaintanceLevel.DIRECT))))

        val jsonObject = firstContactJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeContactsSection.TAG_HIDDEN]?.booleanOrNull)
    }

    @Test
    fun `create should mark hidden true when acquaintance level is group or deleted`() {
        setup(
            listOf(createContactModelMock(TestData.createContactModelData(acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED))),
        )

        val jsonObject = firstContactJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeContactsSection.TAG_HIDDEN]?.booleanOrNull)
    }

    @Test
    fun `create should map firstname`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(firstName = "Jane"))))

        val jsonObject = firstContactJsonObject()

        assertEquals("Jane", jsonObject[ThreemaSafeContactsSection.TAG_FIRST_NAME]?.stringOrNull)
    }

    @Test
    fun `create should map lastname`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(lastName = "Doe"))))

        val jsonObject = firstContactJsonObject()

        assertEquals("Doe", jsonObject[ThreemaSafeContactsSection.TAG_LAST_NAME]?.stringOrNull)
    }

    @Test
    fun `create should map nickname when present`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(nickname = "janey"))))

        val jsonObject = firstContactJsonObject()

        assertEquals("janey", jsonObject[ThreemaSafeContactsSection.TAG_NICKNAME]?.stringOrNull)
    }

    @Test
    fun `create should map nickname to empty string when null`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(nickname = null))))

        val jsonObject = firstContactJsonObject()

        assertEquals("", jsonObject[ThreemaSafeContactsSection.TAG_NICKNAME]?.stringOrNull)
    }

    @Test
    fun `create should map last update when present`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(lastUpdateAt = Instant.ofEpochMilli(5_000L)))))

        val jsonObject = firstContactJsonObject()

        assertEquals(5_000L, jsonObject[ThreemaSafeContactsSection.TAG_LAST_UPDATE]?.longOrNull)
    }

    @Test
    fun `create should not include last update key when null`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(lastUpdateAt = null))))

        val jsonObject = firstContactJsonObject()

        assertEquals(false, jsonObject.containsKey(ThreemaSafeContactsSection.TAG_LAST_UPDATE))
    }

    @Test
    fun `create should mark private false when conversation category is default`() {
        setup(
            listOf(createContactModelMock(TestData.createContactModelData(conversationCategory = ConversationCategory.DEFAULT))),
        )

        val jsonObject = firstContactJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeContactsSection.TAG_PRIVATE]?.booleanOrNull)
    }

    @Test
    fun `create should mark private true when conversation category is protected`() {
        setup(
            listOf(createContactModelMock(TestData.createContactModelData(conversationCategory = ConversationCategory.PROTECTED))),
        )

        val jsonObject = firstContactJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeContactsSection.TAG_PRIVATE]?.booleanOrNull)
    }

    @Test
    fun `create should map read receipt policy default`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(readReceiptPolicy = ReadReceiptPolicy.DEFAULT))))

        val jsonObject = firstContactJsonObject()

        assertEquals(0, jsonObject[ThreemaSafeContactsSection.TAG_READ_RECEIPTS]?.intOrNull)
    }

    @Test
    fun `create should map read receipt policy send`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(readReceiptPolicy = ReadReceiptPolicy.SEND))))

        val jsonObject = firstContactJsonObject()

        assertEquals(1, jsonObject[ThreemaSafeContactsSection.TAG_READ_RECEIPTS]?.intOrNull)
    }

    @Test
    fun `create should map read receipt policy don't send`() {
        setup(listOf(createContactModelMock(TestData.createContactModelData(readReceiptPolicy = ReadReceiptPolicy.DONT_SEND))))

        val jsonObject = firstContactJsonObject()

        assertEquals(2, jsonObject[ThreemaSafeContactsSection.TAG_READ_RECEIPTS]?.intOrNull)
    }

    @Test
    fun `create should map typing indicator policy default`() {
        setup(
            listOf(createContactModelMock(TestData.createContactModelData(typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT))),
        )

        val jsonObject = firstContactJsonObject()

        assertEquals(0, jsonObject[ThreemaSafeContactsSection.TAG_TYPING_INDICATORS]?.intOrNull)
    }

    @Test
    fun `create should map typing indicator policy send`() {
        setup(
            listOf(createContactModelMock(TestData.createContactModelData(typingIndicatorPolicy = TypingIndicatorPolicy.SEND))),
        )

        val jsonObject = firstContactJsonObject()

        assertEquals(1, jsonObject[ThreemaSafeContactsSection.TAG_TYPING_INDICATORS]?.intOrNull)
    }

    @Test
    fun `create should map typing indicator policy don't send`() {
        setup(
            listOf(createContactModelMock(TestData.createContactModelData(typingIndicatorPolicy = TypingIndicatorPolicy.DONT_SEND))),
        )

        val jsonObject = firstContactJsonObject()

        assertEquals(2, jsonObject[ThreemaSafeContactsSection.TAG_TYPING_INDICATORS]?.intOrNull)
    }
}
