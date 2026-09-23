package ch.threema.app.androidcontactsync.synchronization

import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.domain.types.Identity
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData

class AndroidContactMatcherTest {
    private val identityLookupClientMock = mockk<IdentityLookupClient>()

    private val androidContactMatcher = AndroidContactMatcher(identityLookupClientMock)

    private var nextId: ULong = 0u

    @Test
    fun `matches contact with a single phone number match`() {
        val phoneNumber = PhoneNumber("+41791111111")
        val contact = createAndroidContact(phoneNumbers = setOf(phoneNumber))

        mockLookupResult(
            phoneNumberToIdentity = mapOf(phoneNumber to TestData.Identities.OTHER_1),
        )

        val result = androidContactMatcher.matchContacts(setOf(contact))

        assertEquals(
            mapOf(
                contact to AndroidContactMatcher.MatchedIdentity(
                    TestData.Identities.OTHER_1,
                    AndroidContactMatcher.MatchSource.Phone(phoneNumber),
                ),
            ),
            result.androidContactMatches,
        )
    }

    @Test
    fun `matches contact with a single email address match`() {
        val emailAddress = EmailAddress("test@example.com")
        val contact = createAndroidContact(emailAddresses = setOf(emailAddress))

        mockLookupResult(
            emailAddressToIdentity = mapOf(emailAddress to TestData.Identities.OTHER_1),
        )

        val result = androidContactMatcher.matchContacts(setOf(contact))

        assertEquals(
            mapOf(
                contact to AndroidContactMatcher.MatchedIdentity(TestData.Identities.OTHER_1, AndroidContactMatcher.MatchSource.Email(emailAddress)),
            ),
            result.androidContactMatches,
        )
    }

    @Test
    fun `prefers phone number match over email match for the same identity`() {
        val phoneNumber = PhoneNumber("+41791111111")
        val emailAddress = EmailAddress("test@example.com")
        val contact = createAndroidContact(phoneNumbers = setOf(phoneNumber), emailAddresses = setOf(emailAddress))

        mockLookupResult(
            phoneNumberToIdentity = mapOf(phoneNumber to TestData.Identities.OTHER_1),
            emailAddressToIdentity = mapOf(emailAddress to TestData.Identities.OTHER_1),
        )

        val result = androidContactMatcher.matchContacts(setOf(contact))

        assertEquals(
            mapOf(
                contact to AndroidContactMatcher.MatchedIdentity(
                    TestData.Identities.OTHER_1,
                    AndroidContactMatcher.MatchSource.Phone(phoneNumber),
                ),
            ),
            result.androidContactMatches,
        )
    }

    @Test
    fun `drops contact that matches zero identities`() {
        val contact = createAndroidContact(phoneNumbers = setOf(PhoneNumber("+41791111111")))

        mockLookupResult()

        val result = androidContactMatcher.matchContacts(setOf(contact))

        assertEquals(emptyMap(), result.androidContactMatches)
    }

    @Test
    fun `drops contact that matches several identities via phone and email`() {
        val phoneNumber = PhoneNumber("+41791111111")
        val emailAddress = EmailAddress("test@example.com")
        val contact = createAndroidContact(phoneNumbers = setOf(phoneNumber), emailAddresses = setOf(emailAddress))

        mockLookupResult(
            phoneNumberToIdentity = mapOf(phoneNumber to TestData.Identities.OTHER_1),
            emailAddressToIdentity = mapOf(emailAddress to TestData.Identities.OTHER_2),
        )

        val result = androidContactMatcher.matchContacts(setOf(contact))

        assertEquals(emptyMap(), result.androidContactMatches)
    }

    @Test
    fun `drops identity that is claimed by several contacts`() {
        val phoneNumber1 = PhoneNumber("+41791111111")
        val phoneNumber2 = PhoneNumber("+41792222222")
        val contact1 = createAndroidContact(phoneNumbers = setOf(phoneNumber1))
        val contact2 = createAndroidContact(phoneNumbers = setOf(phoneNumber2))

        mockLookupResult(
            phoneNumberToIdentity = mapOf(
                phoneNumber1 to TestData.Identities.OTHER_1,
                phoneNumber2 to TestData.Identities.OTHER_1,
            ),
        )

        val result = androidContactMatcher.matchContacts(setOf(contact1, contact2))

        assertEquals(emptyMap(), result.androidContactMatches)
    }

    @Test
    fun `drops contact with several candidate identities and other contact claiming one of them`() {
        // contact1 matches OTHER_1, OTHER_2, OTHER_3 - contact2 matches OTHER_1
        val phoneNumber1 = PhoneNumber("+41791111111")
        val phoneNumber2 = PhoneNumber("+41792222222")
        val phoneNumber3 = PhoneNumber("+41793333333")
        val sharedPhoneNumber = PhoneNumber("+41794444444")

        val contact1 = createAndroidContact(phoneNumbers = setOf(phoneNumber1, phoneNumber2, phoneNumber3))
        val contact2 = createAndroidContact(phoneNumbers = setOf(sharedPhoneNumber))

        mockLookupResult(
            phoneNumberToIdentity = mapOf(
                phoneNumber1 to TestData.Identities.OTHER_1,
                phoneNumber2 to TestData.Identities.OTHER_2,
                phoneNumber3 to TestData.Identities.OTHER_3,
                sharedPhoneNumber to TestData.Identities.OTHER_1,
            ),
        )

        val result = androidContactMatcher.matchContacts(setOf(contact1, contact2))

        assertEquals(emptyMap(), result.androidContactMatches)
    }

    @Test
    fun `keeps multiple contacts that each uniquely match a different identity`() {
        val phoneNumber1 = PhoneNumber("+41791111111")
        val emailAddress2 = EmailAddress("test2@example.com")
        val contact1 = createAndroidContact(phoneNumbers = setOf(phoneNumber1))
        val contact2 = createAndroidContact(emailAddresses = setOf(emailAddress2))

        mockLookupResult(
            phoneNumberToIdentity = mapOf(phoneNumber1 to TestData.Identities.OTHER_1),
            emailAddressToIdentity = mapOf(emailAddress2 to TestData.Identities.OTHER_2),
        )

        val result = androidContactMatcher.matchContacts(setOf(contact1, contact2))

        assertEquals(
            mapOf(
                contact1 to AndroidContactMatcher.MatchedIdentity(TestData.Identities.OTHER_1, AndroidContactMatcher.MatchSource.Phone(phoneNumber1)),
                contact2 to AndroidContactMatcher.MatchedIdentity(
                    TestData.Identities.OTHER_2,
                    AndroidContactMatcher.MatchSource.Email(emailAddress2),
                ),
            ),
            result.androidContactMatches,
        )
    }

    @Test
    fun `returns empty map for empty input`() {
        mockLookupResult()

        val result = androidContactMatcher.matchContacts(emptySet())

        assertEquals(emptyMap(), result.androidContactMatches)
    }

    @Test
    fun `passes through the full identityToPublicKey map from the lookup, unfiltered by matches`() {
        // OTHER_1 is a valid 1:1 match, OTHER_2 is dropped because two contacts claim it - but its public key
        // should still be present in the result, since identityToPublicKey is not filtered by match outcome.
        val phoneNumber1 = PhoneNumber("+41791111111")
        val phoneNumber2 = PhoneNumber("+41792222222")
        val phoneNumber3 = PhoneNumber("+41793333333")
        val contact1 = createAndroidContact(phoneNumbers = setOf(phoneNumber1))
        val contact2 = createAndroidContact(phoneNumbers = setOf(phoneNumber2))
        val contact3 = createAndroidContact(phoneNumbers = setOf(phoneNumber3))

        val publicKey1 = ByteArray(32) { 1 }
        val publicKey2 = ByteArray(32) { 2 }

        mockLookupResult(
            phoneNumberToIdentity = mapOf(
                phoneNumber1 to TestData.Identities.OTHER_1,
                phoneNumber2 to TestData.Identities.OTHER_2,
                phoneNumber3 to TestData.Identities.OTHER_2,
            ),
            identityToPublicKey = mapOf(
                TestData.Identities.OTHER_1 to publicKey1,
                TestData.Identities.OTHER_2 to publicKey2,
            ),
        )

        val result = androidContactMatcher.matchContacts(setOf(contact1, contact2, contact3))

        assertEquals(
            mapOf(
                contact1 to AndroidContactMatcher.MatchedIdentity(
                    TestData.Identities.OTHER_1,
                    AndroidContactMatcher.MatchSource.Phone(phoneNumber1),
                ),
            ),
            result.androidContactMatches,
        )
        assertEquals(
            mapOf(TestData.Identities.OTHER_1 to publicKey1, TestData.Identities.OTHER_2 to publicKey2),
            result.identityToPublicKey,
        )
    }

    private fun mockLookupResult(
        phoneNumberToIdentity: Map<PhoneNumber, Identity> = emptyMap(),
        emailAddressToIdentity: Map<EmailAddress, Identity> = emptyMap(),
        identityToPublicKey: Map<Identity, ByteArray> = emptyMap(),
    ) {
        every {
            identityLookupClientMock.lookupPhoneAndEmail(any(), any())
        } returns IdentityLookupClient.IdentityLookup(
            phoneNumberToIdentity = phoneNumberToIdentity,
            emailAddressToIdentity = emailAddressToIdentity,
            identityToPublicKey = identityToPublicKey,
        )
    }

    private fun createAndroidContact(
        phoneNumbers: Set<PhoneNumber> = emptySet(),
        emailAddresses: Set<EmailAddress> = emptySet(),
    ): AndroidContact {
        val id = nextId++
        val lookupInfoMock: LookupInfo = mockk()
        val rawContact = RawContact(
            rawContactId = RawContactId(id),
            lookupInfo = lookupInfoMock,
            phoneNumbers = phoneNumbers,
            emailAddresses = emailAddresses,
            structuredNames = emptySet(),
        )
        return AndroidContact(
            lookupInfo = lookupInfoMock,
            rawContacts = setOf(rawContact),
        )
    }
}
