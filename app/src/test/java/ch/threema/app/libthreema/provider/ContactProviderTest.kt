package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.conversion.toContactModelData
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.FileService
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.Contact as LibthreemaContact
import ch.threema.libthreema.ContactWithProfilePictures as LibthreemaContactWithProfilePictures
import ch.threema.libthreema.ProfilePicture as LibthreemaProfilePicture
import ch.threema.libthreema.ProfilePictureMediaType as LibthreemaProfilePictureMediaType
import ch.threema.libthreema.ProviderException as LibthreemaProviderException
import ch.threema.test.TestIdentityProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import testdata.TestData

class ContactProviderTest {

    private val myIdentity = TestData.Identities.ME.value
    private val guaranteedIdentityProvider = GuaranteedIdentityProvider(
        TestIdentityProvider(
            identity = Identity(myIdentity),
        ),
    )

    @Test
    fun `is explicitly blocked works`() {
        val blockedIdentity = TestData.Identities.OTHER_1.value
        val normalIdentity = TestData.Identities.OTHER_2.value
        val blockedIdentitiesServiceMock = mockk<BlockedIdentitiesService> {
            every { isBlocked(blockedIdentity) } returns true
            every { isBlocked(normalIdentity) } returns false
        }

        val settingsProvider = createContactProvider(
            blockedIdentitiesService = blockedIdentitiesServiceMock,
        )

        assertTrue(settingsProvider.isExplicitlyBlocked(blockedIdentity))
        assertFalse(settingsProvider.isExplicitlyBlocked(normalIdentity))
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            settingsProvider.isExplicitlyBlocked(myIdentity)
        }
    }

    @Test
    fun `has contact works`() {
        // Arrange
        val knownIdentity = TestData.Identities.OTHER_1.value
        val unknownIdentity = TestData.Identities.OTHER_2.value
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { existsByIdentity(any<IdentityString>()) } answers {
                firstArg<IdentityString>() == knownIdentity
            }
        }
        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
        )

        // Act
        val knownContactExists = contactProvider.has(knownIdentity)
        val unknownContactExists = contactProvider.has(unknownIdentity)

        // Assert
        assertTrue(knownContactExists)
        assertFalse(unknownContactExists)
    }

    @Test
    fun `has fails for user's identity`() {
        val contactProvider = createContactProvider()
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.has(myIdentity)
        }
    }

    @Test
    fun `count existing contacts works`() {
        // Arrange
        val knownIdentities = setOf(
            TestData.Identities.OTHER_1.value,
            TestData.Identities.OTHER_2.value,
            TestData.Identities.OTHER_3.value,
        )
        val unknownIdentity = TestData.Identities.OTHER_4.value
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { countExisting(any()) } answers {
                val identities = firstArg<Set<IdentityString>>()
                // We just mock a contact if it should exist
                knownIdentities.intersect(identities).size.toUInt()
            }
        }
        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
        )

        // Act
        val countExistingFirstSet = contactProvider.countExisting(knownIdentities.toList())
        val countExistingSecondSet =
            contactProvider.countExisting(knownIdentities.toList() + unknownIdentity)
        val countExistingThirdSet = contactProvider.countExisting(emptyList())
        val countExistingFourthSet = contactProvider.countExisting(listOf(knownIdentities.first()))
        val countExistingFifthSet = contactProvider.countExisting(listOf(unknownIdentity))

        // Assert
        assertEquals(3u, countExistingFirstSet)
        assertEquals(3u, countExistingSecondSet)
        assertEquals(0u, countExistingThirdSet)
        assertEquals(1u, countExistingFourthSet)
        assertEquals(0u, countExistingFifthSet)
    }

    @Test
    fun `count existing fails with user's identity`() {
        val contactProvider = createContactProvider()
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.countExisting(listOf(myIdentity))
        }
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.countExisting(listOf(TestData.Identities.OTHER_1.value, myIdentity))
        }
    }

    @Test
    fun `getting contact works`() {
        // Arrange
        val knownContact = TestData.createContactModel(
            identity = TestData.Identities.OTHER_1,
            publicKey = ByteArray(32),
            createdAt = Instant.ofEpochMilli(42),
            firstname = "firstName",
            lastname = "lastName",
            nickname = "nickname",
        )
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getByIdentity(any<IdentityString>()) } answers {
                if (firstArg<IdentityString>() == knownContact.identity) {
                    knownContact
                } else {
                    null
                }
            }
        }

        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
        )

        // Act
        val knownContactResult = contactProvider.get(knownContact.identity)
        val unknownContactResult = contactProvider.get("00000000")

        // Assert
        assertNotNull(knownContactResult)
        assertEquals(knownContact.data?.firstName, knownContactResult.firstName)
        assertEquals(knownContact.data?.lastName, knownContactResult.lastName)
        assertEquals(knownContact.data?.publicKey, knownContactResult.publicKey)
        assertEquals(ch.threema.libthreema.VerificationLevel.FULLY_VERIFIED, knownContactResult.verificationLevel)
        assertEquals(ch.threema.libthreema.WorkVerificationLevel.NONE, knownContactResult.workVerificationLevel)
        assertEquals(ch.threema.libthreema.IdentityType.REGULAR, knownContactResult.identityType)
        assertEquals(ch.threema.libthreema.AcquaintanceLevel.DIRECT, knownContactResult.acquaintanceLevel)
        assertNull(unknownContactResult)
    }

    @Test
    fun `getting user as contact fails`() {
        val contactProvider = createContactProvider()
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.get(myIdentity)
        }
    }

    @Test
    fun `getting user as contact with profile picture fails`() {
        val contactProvider = createContactProvider()
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.getWithProfilePictures(myIdentity)
        }
    }

    @Test
    fun `getting identities works`() {
        // Arrange
        val allContactIdentities = setOf(
            TestData.Identities.OTHER_1.value,
            TestData.Identities.OTHER_2.value,
        )
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAllContactIdentities() } returns allContactIdentities
        }
        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
        )

        // Act
        val allActualIdentities = contactProvider.getIdentities()

        // Assert
        assertEquals(allContactIdentities, allActualIdentities)
    }

    @Test
    fun `adding contacts works`() {
        // Arrange
        val newIdentity1 = TestData.Identities.OTHER_1.value
        val newIdentity2 = TestData.Identities.OTHER_2.value
        val newLibthreemaContactMock1 = mockk<LibthreemaContact> {
            every { identity } returns newIdentity1
        }
        val newLibthreemaContactMock2 = mockk<LibthreemaContact> {
            every { identity } returns newIdentity2
        }
        val newContactModelData1 = TestData.createContactModelData(identity = Identity(newIdentity1))
        val newContactModelData2 = TestData.createContactModelData(identity = Identity(newIdentity2))
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { countExisting(setOf(newIdentity1, newIdentity2)) } returns 0u
            every { persistNewContacts(setOf(newContactModelData1, newContactModelData2)) } just Runs
        }
        mockkStatic("ch.threema.app.libthreema.conversion.ContactConversionKt")
        every { newLibthreemaContactMock1.toContactModelData() } returns newContactModelData1
        every { newLibthreemaContactMock2.toContactModelData() } returns newContactModelData2
        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
        )

        // Act
        contactProvider.add(listOf(newLibthreemaContactMock1, newLibthreemaContactMock2))

        // Assert
        verify(exactly = 1) { contactModelRepositoryMock.persistNewContacts(setOf(newContactModelData1, newContactModelData2)) }

        unmockkStatic("ch.threema.app.libthreema.conversion.ContactConversionKt")
    }

    @Test
    fun `adding user as contact fails`() {
        val contactProvider = createContactProvider()
        val userContact = mockk<LibthreemaContact> {
            every { identity } returns myIdentity
        }
        val other1Contact = mockk<LibthreemaContact> {
            every { identity } returns TestData.Identities.OTHER_1.value
        }
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.add(listOf(userContact))
        }
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            // Adding contacts should also fail if the user is present in the list.
            contactProvider.add(listOf(other1Contact, userContact))
        }
    }

    @Test
    fun `adding existing contact fails`() {
        val unknownIdentity = TestData.Identities.OTHER_1.value
        val existingIdentity = TestData.Identities.OTHER_2.value

        val unknownLibthreemaContactMock = mockk<LibthreemaContact> {
            every { identity } returns unknownIdentity
        }
        val existingLibthreemaContactMock = mockk<LibthreemaContact> {
            every { identity } returns existingIdentity
        }

        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { countExisting(setOf(unknownIdentity, existingIdentity)) } returns 1u
        }
        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
        )

        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.add(listOf(unknownLibthreemaContactMock, existingLibthreemaContactMock))
        }
    }

    @Test
    fun `adding contacts with profile picture works`() {
        // Arrange
        val newIdentity1 = TestData.Identities.OTHER_1.value
        val newIdentity2 = TestData.Identities.OTHER_2.value
        val newLibthreemaContactMock1 = mockk<LibthreemaContact> {
            every { identity } returns newIdentity1
        }
        val contactDefinedProfilePictureData = byteArrayOf(42)
        val libthreemaContactDefinedProfilePictureMock1 = mockk<LibthreemaProfilePicture> {
            every { data } returns contactDefinedProfilePictureData
        }
        val userDefinedProfilePictureData = byteArrayOf(43)
        val libthreemaUserDefinedProfilePictureMock1 = mockk<LibthreemaProfilePicture> {
            every { data } returns userDefinedProfilePictureData
        }
        val newLibthreemaContactWithProfilePictureMock1 = mockk<LibthreemaContactWithProfilePictures> {
            every { contact } returns newLibthreemaContactMock1
            every { component1() } returns newLibthreemaContactMock1
            every { contactDefinedProfilePicture } returns libthreemaContactDefinedProfilePictureMock1
            every { component2() } returns libthreemaContactDefinedProfilePictureMock1
            every { userDefinedProfilePicture } returns libthreemaUserDefinedProfilePictureMock1
            every { component3() } returns libthreemaUserDefinedProfilePictureMock1
        }
        val newLibthreemaContactMock2 = mockk<LibthreemaContact> {
            every { identity } returns newIdentity2
        }
        val newLibthreemaContactWithProfilePictureMock2 = mockk<LibthreemaContactWithProfilePictures> {
            every { contact } returns newLibthreemaContactMock2
            every { component1() } returns newLibthreemaContactMock2
            every { contactDefinedProfilePicture } returns null
            every { component2() } returns null
            every { userDefinedProfilePicture } returns null
            every { component3() } returns null
        }
        val newContactModelData1 = TestData.createContactModelData(identity = Identity(newIdentity1))
        val newContactModelData2 = TestData.createContactModelData(identity = Identity(newIdentity2))
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { countExisting(setOf(newIdentity1, newIdentity2)) } returns 0u
            every { persistNewContacts(setOf(newContactModelData1, newContactModelData2)) } just Runs
        }
        mockkStatic("ch.threema.app.libthreema.conversion.ContactConversionKt")
        every { newLibthreemaContactMock1.toContactModelData() } returns newContactModelData1
        every { newLibthreemaContactMock2.toContactModelData() } returns newContactModelData2
        val fileServiceMock = mockk<FileService> {
            every { writeContactDefinedProfilePicture(newIdentity1, contactDefinedProfilePictureData) } just Runs
            every { writeUserDefinedProfilePicture(newIdentity1, userDefinedProfilePictureData) } just Runs
        }
        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
            fileService = fileServiceMock,
        )

        // Act
        contactProvider.addWithProfilePictures(
            listOf(newLibthreemaContactWithProfilePictureMock1, newLibthreemaContactWithProfilePictureMock2),
        )

        // Assert
        verify(exactly = 1) { contactModelRepositoryMock.persistNewContacts(setOf(newContactModelData1, newContactModelData2)) }
        verify(exactly = 1) {
            fileServiceMock.writeContactDefinedProfilePicture(
                newIdentity1,
                contactDefinedProfilePictureData,
            )
        }
        verify(exactly = 1) {
            fileServiceMock.writeUserDefinedProfilePicture(
                newIdentity1,
                userDefinedProfilePictureData,
            )
        }
        unmockkStatic("ch.threema.app.libthreema.conversion.ContactConversionKt")
    }

    @Test
    fun `adding user as contact with profile picture fails`() {
        val contactProvider = createContactProvider()
        val userContact = mockk<LibthreemaContact> {
            every { identity } returns myIdentity
        }
        val userContactWithProfilePicture = mockk<LibthreemaContactWithProfilePictures> {
            every { contact } returns userContact
            every { component1() } returns userContact
        }
        val other1Contact = mockk<LibthreemaContact> {
            every { identity } returns TestData.Identities.OTHER_1.value
        }
        val other1ContactWithProfilePicture = mockk<LibthreemaContactWithProfilePictures> {
            every { contact } returns other1Contact
            every { component1() } returns other1Contact
        }
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.addWithProfilePictures(listOf(userContactWithProfilePicture))
        }
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            // Adding contacts should also fail if the user is present in the list.
            contactProvider.addWithProfilePictures(listOf(other1ContactWithProfilePicture, userContactWithProfilePicture))
        }
    }

    @Test
    fun `adding existing contact with profile picture fails`() {
        val unknownIdentity = TestData.Identities.OTHER_1.value
        val existingIdentity = TestData.Identities.OTHER_2.value

        val unknownLibthreemaContactMock = mockk<LibthreemaContact> {
            every { identity } returns unknownIdentity
        }
        val unknownLibthreemaContactWithProfilePictureMock = mockk<LibthreemaContactWithProfilePictures> {
            every { contact } returns unknownLibthreemaContactMock
            every { component1() } returns unknownLibthreemaContactMock
        }
        val existingLibthreemaContactMock = mockk<LibthreemaContact> {
            every { identity } returns existingIdentity
        }
        val existingLibthreemaContactWithProfilePictureMock = mockk<LibthreemaContactWithProfilePictures> {
            every { contact } returns existingLibthreemaContactMock
            every { component1() } returns existingLibthreemaContactMock
        }

        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { countExisting(setOf(unknownIdentity, existingIdentity)) } returns 1u
        }
        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
        )

        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.addWithProfilePictures(
                listOf(unknownLibthreemaContactWithProfilePictureMock, existingLibthreemaContactWithProfilePictureMock),
            )
        }
    }

    @Test
    fun `setting contact defined profile picture works`() {
        // Arrange
        val identity = TestData.Identities.OTHER_1.value
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { existsByIdentity(identity) } returns true
        }
        val expectedProfilePictureBytes = byteArrayOf(0, 1, 2, 3)
        var actualProfilePictureBytes: ByteArray? = null

        val fileServiceMock = mockk<FileService> {
            every { writeContactDefinedProfilePicture(any(), any<ByteArray>()) } answers {
                actualProfilePictureBytes = secondArg()
            }
        }

        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
            fileService = fileServiceMock,
        )

        // Act
        contactProvider.setContactDefinedProfilePicture(
            identity,
            LibthreemaProfilePicture(
                mediaType = LibthreemaProfilePictureMediaType.JPEG,
                data = expectedProfilePictureBytes,
            ),
        )

        // Assert
        assertContentEquals(expectedProfilePictureBytes, actualProfilePictureBytes)
    }

    @Test
    fun `removing contact defined profile picture works`() {
        // Arrange
        val identity = TestData.Identities.OTHER_1.value
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { existsByIdentity(identity) } returns true
        }

        val fileServiceMock = mockk<FileService> {
            every { removeContactDefinedProfilePicture(identity) } just Runs
        }

        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
            fileService = fileServiceMock,
        )

        // Act
        contactProvider.setContactDefinedProfilePicture(identity, null)

        // Assert
        verify(exactly = 1) { fileServiceMock.removeContactDefinedProfilePicture(identity) }
    }

    @Test
    fun `setting contact defined profile picture for user fails`() {
        val contactProvider = createContactProvider()
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.setContactDefinedProfilePicture(myIdentity, null)
        }
    }

    @Test
    fun `setting contact defined profile picture for unknown contact fails`() {
        val unknownIdentity = TestData.Identities.OTHER_1.value
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { existsByIdentity(unknownIdentity) } returns false
        }
        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
        )
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.setContactDefinedProfilePicture(unknownIdentity, null)
        }
    }

    @Test
    fun `setting user defined profile picture works`() {
        // Arrange
        val identity = TestData.Identities.OTHER_1.value
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { existsByIdentity(identity) } returns true
        }
        val expectedProfilePictureBytes = byteArrayOf(0, 1, 2, 3)
        var actualProfilePictureBytes: ByteArray? = null

        val fileServiceMock = mockk<FileService> {
            every { writeUserDefinedProfilePicture(any(), any<ByteArray>()) } answers {
                actualProfilePictureBytes = secondArg()
            }
        }

        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
            fileService = fileServiceMock,
        )

        // Act
        contactProvider.setUserDefinedProfilePicture(
            identity,
            LibthreemaProfilePicture(
                mediaType = LibthreemaProfilePictureMediaType.JPEG,
                data = expectedProfilePictureBytes,
            ),
        )

        // Assert
        assertContentEquals(expectedProfilePictureBytes, actualProfilePictureBytes)
    }

    @Test
    fun `removing user defined profile picture works`() {
        // Arrange
        val identity = TestData.Identities.OTHER_1.value
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { existsByIdentity(identity) } returns true
        }

        val fileServiceMock = mockk<FileService> {
            every { removeUserDefinedProfilePicture(identity) } returns true
        }

        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
            fileService = fileServiceMock,
        )

        // Act
        contactProvider.setUserDefinedProfilePicture(identity, null)

        // Assert
        verify(exactly = 1) { fileServiceMock.removeUserDefinedProfilePicture(identity) }
    }

    @Test
    fun `setting user defined profile picture for user fails`() {
        val contactProvider = createContactProvider()
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.setUserDefinedProfilePicture(myIdentity, null)
        }
    }

    @Test
    fun `setting user defined profile picture for unknown contact fails`() {
        val unknownIdentity = TestData.Identities.OTHER_1.value
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { existsByIdentity(unknownIdentity) } returns false
        }
        val contactProvider = createContactProvider(
            contactModelRepository = contactModelRepositoryMock,
        )
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.setUserDefinedProfilePicture(unknownIdentity, null)
        }
    }

    private fun createContactProvider(
        fileService: FileService = mockk(),
        contactModelRepository: ContactModelRepository = mockk(),
        blockedIdentitiesService: BlockedIdentitiesService = mockk(),
        guaranteedIdentityProvider: GuaranteedIdentityProvider = this.guaranteedIdentityProvider,
    ) = ContactProvider(
        fileService = fileService,
        contactModelRepository = contactModelRepository,
        blockedIdentitiesService = blockedIdentitiesService,
        guaranteedIdentityProvider = guaranteedIdentityProvider,
    )
}
