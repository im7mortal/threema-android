package ch.threema.data.storage

import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.Identity
import ch.threema.test.TestData.PUBLIC_KEY
import ch.threema.test.TestDatabaseProvider
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqliteDatabaseBackendTest {

    private lateinit var databaseBackend: SqliteDatabaseBackend

    @BeforeTest
    fun setUp() {
        databaseBackend = SqliteDatabaseBackend(
            databaseProvider = TestDatabaseProvider(),
            identityProvider = TestIdentityProvider(identity = MY_IDENTITY),
        )
    }

    @Test
    fun canCreateMultipleDifferentContacts() {
        databaseBackend.createContact(CONTACT)
        val otherContact = CONTACT.copy(
            identity = "TEST9999",
            publicKey = ByteArray(32) { 2 },
        )
        databaseBackend.createContact(otherContact)

        val storedIdentities = databaseBackend.getAllContacts().map { it.identity }
        assertEquals(listOf(CONTACT.identity, otherContact.identity), storedIdentities)
    }

    @Test
    fun cannotCreateContactsWithInvalidIdentity() {
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(CONTACT.copy(identity = "INVALID"))
        }
    }

    @Test
    fun cannotCreateContactsWithUserIdentity() {
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(CONTACT.copy(identity = MY_IDENTITY.value))
        }
    }

    @Test
    fun cannotCreateContactsWithWrongLengthPublicKey() {
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(CONTACT.copy(publicKey = ByteArray(33) { 1 }))
        }
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(CONTACT.copy(publicKey = ByteArray(31) { 1 }))
        }
    }

    @Test
    fun cannotCreateContactsWithAllZeroesPublicKey() {
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(CONTACT.copy(publicKey = ByteArray(32)))
        }
    }

    @Test
    fun cannotCreateMultipleContactsWithSameIdentity() {
        databaseBackend.createContact(CONTACT)
        assertFailsWith<DatabaseException> {
            databaseBackend.createContact(CONTACT)
        }
    }

    @Test
    fun cannotCreateMultipleContactsWithSamePublicKey() {
        databaseBackend.createContact(CONTACT)
        assertFailsWith<DatabaseException> {
            val otherContact = CONTACT.copy(identity = "TEST9999")
            databaseBackend.createContact(otherContact)
        }
    }

    companion object {
        private val MY_IDENTITY = Identity("00000000")

        private val CONTACT = DbContact(
            identity = "TESTTEST",
            publicKey = PUBLIC_KEY,
            createdAt = utcDate(2026, 4, 15),
            lastUpdateAt = null,
            firstName = "Testy",
            lastName = "McTestface",
            nickname = null,
            colorIndex = 0,
            verificationLevel = VerificationLevel.UNVERIFIED,
            workVerificationLevel = WorkVerificationLevel.NONE,
            identityType = IdentityType.REGULAR,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
            activityState = IdentityState.ACTIVE,
            syncState = ContactSyncState.INITIAL,
            featureMask = 0UL,
            readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
            typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
            conversationVisibility = ConversationVisibility.NORMAL,
            androidContactLookupKey = null,
            localAvatarExpires = null,
            isRestored = false,
            profilePictureBlobId = null,
            jobTitle = null,
            department = null,
            notificationTriggerPolicyOverridePolicy = null,
            notificationTriggerPolicyOverrideExpiresAt = null,
            availabilityStatusSet = null,
            workLastFullSyncAt = null,
        )
    }
}
