package ch.threema.app.androidcontactsync.synchronization

import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.LocaleService
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.APIConnector.MatchIdentityResult
import ch.threema.domain.stores.IdentityStore
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import testdata.TestData

class IdentityLookupClientTest {

    private val apiConnectorMock = mockk<APIConnector>()
    private val identityStoreMock = mockk<IdentityStore>()
    private val localeServiceMock = mockk<LocaleService> {
        every { countryIsoCode } returns "CH"
    }
    private val preferenceServiceMock = mockk<PreferenceService>()

    private val identityLookupClient = IdentityLookupClient(apiConnectorMock, identityStoreMock, localeServiceMock, preferenceServiceMock)

    @Test
    fun `returns unique phone and email matches`() {
        val phoneNumber = PhoneNumber("+41791234567")
        val emailAddress = EmailAddress("test@example.com")
        val publicKey = ByteArray(32) { 1 }

        every {
            apiConnectorMock.matchIdentities(any(), any(), any(), any(), any(), any())
        } returns mapOf(
            TestData.Identities.OTHER_1.value to matchResult(
                publicKey = publicKey,
                phoneNumber = phoneNumber.phoneNumber,
                emailAddress = emailAddress.emailAddress,
            ),
        )

        val result = identityLookupClient.lookupPhoneAndEmail(
            phoneNumbers = setOf(phoneNumber),
            emailAddresses = setOf(emailAddress),
        )

        assertEquals(mapOf(phoneNumber to TestData.Identities.OTHER_1), result.phoneNumberToIdentity)
        assertEquals(mapOf(emailAddress to TestData.Identities.OTHER_1), result.emailAddressToIdentity)
        assertEquals(mapOf(TestData.Identities.OTHER_1 to publicKey), result.identityToPublicKey)
    }

    @Test
    fun `drops phone number that matches multiple identities but still returns public keys`() {
        val phoneNumber = PhoneNumber("+41791234567")
        val publicKey1 = ByteArray(32) { 1 }
        val publicKey2 = ByteArray(32) { 2 }

        // Note that this can only happen if the server returns invalid data
        every {
            apiConnectorMock.matchIdentities(any(), any(), any(), any(), any(), any())
        } returns mapOf(
            TestData.Identities.OTHER_1.value to matchResult(publicKey = publicKey1, phoneNumber = phoneNumber.phoneNumber),
            TestData.Identities.OTHER_2.value to matchResult(publicKey = publicKey2, phoneNumber = phoneNumber.phoneNumber),
        )

        val result = identityLookupClient.lookupPhoneAndEmail(
            phoneNumbers = setOf(phoneNumber),
            emailAddresses = emptySet(),
        )

        assertEquals(emptyMap(), result.phoneNumberToIdentity)
        assertEquals(
            mapOf(TestData.Identities.OTHER_1 to publicKey1, TestData.Identities.OTHER_2 to publicKey2),
            result.identityToPublicKey,
        )
    }

    @Test
    fun `drops email address that matches multiple identities`() {
        val emailAddress = EmailAddress("test@example.com")

        // Note that this can only happen if the server returns invalid data.
        every {
            apiConnectorMock.matchIdentities(any(), any(), any(), any(), any(), any())
        } returns mapOf(
            TestData.Identities.OTHER_1.value to matchResult(emailAddress = emailAddress.emailAddress),
            TestData.Identities.OTHER_2.value to matchResult(emailAddress = emailAddress.emailAddress),
        )

        val result = identityLookupClient.lookupPhoneAndEmail(
            phoneNumbers = emptySet(),
            emailAddresses = setOf(emailAddress),
        )

        assertEquals(emptyMap(), result.emailAddressToIdentity)
    }

    @Test
    fun `returns empty result when no matches are found`() {
        every {
            apiConnectorMock.matchIdentities(any(), any(), any(), any(), any(), any())
        } returns emptyMap()

        val result = identityLookupClient.lookupPhoneAndEmail(
            phoneNumbers = setOf(PhoneNumber("+41791234567")),
            emailAddresses = setOf(EmailAddress("test@example.com")),
        )

        assertEquals(emptyMap(), result.phoneNumberToIdentity)
        assertEquals(emptyMap(), result.emailAddressToIdentity)
        assertEquals(emptyMap(), result.identityToPublicKey)
    }

    @Test
    fun `records public key even when entry has neither phone number nor email address`() {
        val publicKey = ByteArray(32) { 1 }

        every {
            apiConnectorMock.matchIdentities(any(), any(), any(), any(), any(), any())
        } returns mapOf(
            TestData.Identities.OTHER_1.value to matchResult(publicKey = publicKey, phoneNumber = null, emailAddress = null),
        )

        val result = identityLookupClient.lookupPhoneAndEmail(
            phoneNumbers = emptySet(),
            emailAddresses = emptySet(),
        )

        assertEquals(emptyMap(), result.phoneNumberToIdentity)
        assertEquals(emptyMap(), result.emailAddressToIdentity)
        assertEquals(mapOf(TestData.Identities.OTHER_1 to publicKey), result.identityToPublicKey)
    }

    @Test
    fun `handles multiple distinct phone and email matches`() {
        val phoneNumber1 = PhoneNumber("+41791111111")
        val phoneNumber2 = PhoneNumber("+41792222222")
        val emailAddress1 = EmailAddress("one@example.com")
        val emailAddress2 = EmailAddress("two@example.com")

        every {
            apiConnectorMock.matchIdentities(any(), any(), any(), any(), any(), any())
        } returns mapOf(
            TestData.Identities.OTHER_1.value to matchResult(phoneNumber = phoneNumber1.phoneNumber),
            TestData.Identities.OTHER_2.value to matchResult(phoneNumber = phoneNumber2.phoneNumber),
            TestData.Identities.OTHER_3.value to matchResult(emailAddress = emailAddress1.emailAddress),
            TestData.Identities.OTHER_4.value to matchResult(emailAddress = emailAddress2.emailAddress),
        )

        val result = identityLookupClient.lookupPhoneAndEmail(
            phoneNumbers = setOf(phoneNumber1, phoneNumber2),
            emailAddresses = setOf(emailAddress1, emailAddress2),
        )

        assertEquals(
            mapOf(phoneNumber1 to TestData.Identities.OTHER_1, phoneNumber2 to TestData.Identities.OTHER_2),
            result.phoneNumberToIdentity,
        )
        assertEquals(
            mapOf(emailAddress1 to TestData.Identities.OTHER_3, emailAddress2 to TestData.Identities.OTHER_4),
            result.emailAddressToIdentity,
        )
        assertEquals(4, result.identityToPublicKey.size)
    }

    @Test
    fun `propagates exception thrown by api connector`() {
        every {
            apiConnectorMock.matchIdentities(any(), any(), any(), any(), any(), any())
        } throws RuntimeException("network error")

        assertFailsWith<RuntimeException> {
            identityLookupClient.lookupPhoneAndEmail(
                phoneNumbers = setOf(PhoneNumber("+41791234567")),
                emailAddresses = emptySet(),
            )
        }
    }

    private fun matchResult(
        publicKey: ByteArray = ByteArray(32),
        phoneNumber: String? = null,
        emailAddress: String? = null,
    ) = MatchIdentityResult(publicKey).apply {
        this.phoneNumber = phoneNumber
        this.emailAddress = emailAddress
    }
}
