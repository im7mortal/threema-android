package ch.threema.app.protocolsteps

import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.UserState
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.types.Identity
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.models.GroupMessageModel
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.testDispatcherProvider
import ch.threema.testhelpers.utcDate
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import testdata.TestData.GroupIdentities
import testdata.TestData.Identities
import testdata.TestData.createContactModel
import testdata.TestData.createGroupModel

class GroupSpamPotentialDetectionStepsTest {

    private companion object {
        const val GROUP_DATABASE_ID = 42L

        val CREATOR_CREATED_AT = utcDate(2026, 1, 1)
    }

    private lateinit var contactModelRepositoryMock: ContactModelRepository
    private lateinit var groupMessageModelFactoryMock: GroupMessageModelFactory
    private lateinit var databaseBackendMock: DatabaseBackend

    private fun createSteps(dispatcherProvider: DispatcherProvider) =
        GroupSpamPotentialDetectionSteps(
            contactModelRepository = contactModelRepositoryMock,
            groupMessageModelFactory = groupMessageModelFactoryMock,
            dispatcherProvider = dispatcherProvider,
        )

    @Before
    fun before() {
        contactModelRepositoryMock = mockk<ContactModelRepository>()
        groupMessageModelFactoryMock = mockk<GroupMessageModelFactory>()
        databaseBackendMock = mockk<DatabaseBackend>()
    }

    @Test
    fun `returns false if the group model has no data`() = runTest {
        // arrange
        val groupModelMock = mockk<GroupModel> {
            every { groupIdentity } returns GroupIdentities.GROUP_1
            every { data } returns null
        }
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(groupModelMock)

        // assert
        assertFalse(isPotentialSpamGroup)
    }

    @Test
    fun `returns false if the spam reporting state is not-spam`() = runTest {
        // arrange
        val group = createGroup(spamReportingState = ConversationSpamReportingState.NOT_SPAM)
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
    }

    @Test
    fun `returns false if the spam reporting state is reported`() = runTest {
        // arrange
        val group = createGroup(spamReportingState = ConversationSpamReportingState.REPORTED)
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
    }

    @Test
    fun `returns false if the user is the group creator`() = runTest {
        // arrange
        val group = createGroup(
            groupIdentity = GroupIdentities.GROUP_ME,
            ownIdentity = Identities.ME,
        )
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
    }

    @Test
    fun `returns false if the user left the group`() = runTest {
        // arrange
        val group = createGroup(userState = UserState.LEFT)
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
    }

    @Test
    fun `returns false if the user was kicked from the group`() = runTest {
        // arrange
        val group = createGroup(userState = UserState.KICKED)
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
        confirmVerified(contactModelRepositoryMock, groupMessageModelFactoryMock)
    }

    @Test
    fun `returns false if the group creator contact does not exist`() = runTest {
        // arrange
        val group = createGroup()
        every {
            contactModelRepositoryMock.getByIdentity(GroupIdentities.GROUP_1.creatorIdentity)
        } returns null
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
        verify(exactly = 1) {
            contactModelRepositoryMock.getByIdentity(GroupIdentities.GROUP_1.creatorIdentity)
        }
        confirmVerified(groupMessageModelFactoryMock)
    }

    @Test
    fun `returns false if the group creator is server verified`() = runTest {
        // arrange
        val group = createGroup()
        mockCreatorContact(verificationLevel = VerificationLevel.SERVER_VERIFIED)
        stubGroupDatabaseId()
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
        verify(exactly = 1) {
            contactModelRepositoryMock.getByIdentity(GroupIdentities.GROUP_1.creatorIdentity)
        }
        confirmVerified(groupMessageModelFactoryMock)
    }

    @Test
    fun `returns false if the group creator is fully verified`() = runTest {
        // arrange
        val group = createGroup()
        mockCreatorContact(verificationLevel = VerificationLevel.FULLY_VERIFIED)
        stubGroupDatabaseId()
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
        verify(exactly = 1) {
            contactModelRepositoryMock.getByIdentity(GroupIdentities.GROUP_1.creatorIdentity)
        }
        confirmVerified(groupMessageModelFactoryMock)
    }

    @Test
    fun `returns false if the conversation contains an outgoing content message`() = runTest {
        // arrange
        val group = createGroup()
        mockCreatorContact()
        stubGroupDatabaseId()
        every { groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID) } returns true
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
        verify(exactly = 1) {
            groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID)
        }
        verify(exactly = 0) { groupMessageModelFactoryMock.getEarliestIncomingContentMessage(any()) }
    }

    @Test
    fun `returns false if the conversation does not contain an incoming content message`() = runTest {
        // arrange
        val group = createGroup()
        mockCreatorContact()
        stubGroupDatabaseId()
        every { groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID) } returns false
        every {
            groupMessageModelFactoryMock.getEarliestIncomingContentMessage(GROUP_DATABASE_ID)
        } returns null
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
        verify(exactly = 1) {
            groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID)
        }
        verify(exactly = 1) {
            groupMessageModelFactoryMock.getEarliestIncomingContentMessage(GROUP_DATABASE_ID)
        }
    }

    @Test
    fun `returns false if the earliest incoming content message has no creation date`() = runTest {
        // arrange
        val group = createGroup()
        mockCreatorContact()
        stubGroupDatabaseId()
        every { groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID) } returns false
        every {
            groupMessageModelFactoryMock.getEarliestIncomingContentMessage(GROUP_DATABASE_ID)
        } returns createEarliestIncomingContentMessage(createdAt = null)
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
    }

    @Test
    fun `returns false if the timespan between the earliest message and the creator creation is greater than seven days`() = runTest {
        // arrange
        val group = createGroup()
        mockCreatorContact(createdAt = CREATOR_CREATED_AT)
        stubGroupDatabaseId()
        every { groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID) } returns false
        every {
            groupMessageModelFactoryMock.getEarliestIncomingContentMessage(GROUP_DATABASE_ID)
        } returns createEarliestIncomingContentMessage(createdAt = utcDate(2026, 1, 9))
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertFalse(isPotentialSpamGroup)
    }

    @Test
    fun `returns true if the timespan between the earliest message and the creator creation is exactly seven days`() = runTest {
        // arrange
        val group = createGroup()
        mockCreatorContact(createdAt = CREATOR_CREATED_AT)
        stubGroupDatabaseId()
        every { groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID) } returns false
        every {
            groupMessageModelFactoryMock.getEarliestIncomingContentMessage(GROUP_DATABASE_ID)
        } returns createEarliestIncomingContentMessage(createdAt = utcDate(2026, 1, 8))
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertTrue(isPotentialSpamGroup)
    }

    @Test
    fun `returns true if the earliest message was received before the creator was created`() = runTest {
        // arrange
        val group = createGroup()
        mockCreatorContact(createdAt = CREATOR_CREATED_AT)
        stubGroupDatabaseId()
        every { groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID) } returns false
        every {
            groupMessageModelFactoryMock.getEarliestIncomingContentMessage(GROUP_DATABASE_ID)
        } returns createEarliestIncomingContentMessage(createdAt = utcDate(2025, 12, 31))
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertTrue(isPotentialSpamGroup)
    }

    @Test
    fun `returns true if potential spam was detected`() = runTest {
        // arrange
        val group = createGroup()
        mockCreatorContact(createdAt = CREATOR_CREATED_AT)
        stubGroupDatabaseId()
        every { groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID) } returns false
        every {
            groupMessageModelFactoryMock.getEarliestIncomingContentMessage(GROUP_DATABASE_ID)
        } returns createEarliestIncomingContentMessage(createdAt = utcDate(2026, 1, 2))
        val steps = createSteps(testDispatcherProvider())

        // act
        val isPotentialSpamGroup = steps.run(group)

        // assert
        assertTrue(isPotentialSpamGroup)
        verify(exactly = 1) {
            contactModelRepositoryMock.getByIdentity(GroupIdentities.GROUP_1.creatorIdentity)
        }
        verify(exactly = 1) {
            groupMessageModelFactoryMock.existsOutgoingContentMessage(GROUP_DATABASE_ID)
        }
        verify(exactly = 1) {
            groupMessageModelFactoryMock.getEarliestIncomingContentMessage(GROUP_DATABASE_ID)
        }
    }

    private fun createGroup(
        groupIdentity: GroupIdentity = GroupIdentities.GROUP_1,
        userState: UserState = UserState.MEMBER,
        spamReportingState: ConversationSpamReportingState = ConversationSpamReportingState.DEFAULT,
        ownIdentity: Identity = Identities.ME,
    ): GroupModel = createGroupModel(
        groupIdentity = groupIdentity,
        userState = userState,
        spamReportingState = spamReportingState,
        identityProvider = TestIdentityProvider(ownIdentity),
        databaseBackend = databaseBackendMock,
    )

    private fun mockCreatorContact(
        verificationLevel: VerificationLevel = VerificationLevel.UNVERIFIED,
        createdAt: Instant = CREATOR_CREATED_AT,
    ) {
        val creatorContact = createContactModel(
            identity = Identities.OTHER_1,
            verificationLevel = verificationLevel,
            createdAt = createdAt,
        )
        every {
            contactModelRepositoryMock.getByIdentity(GroupIdentities.GROUP_1.creatorIdentity)
        } returns creatorContact
    }

    private fun stubGroupDatabaseId() {
        every { databaseBackendMock.getGroupDatabaseId(GroupIdentities.GROUP_1) } returns GROUP_DATABASE_ID
    }

    private fun createEarliestIncomingContentMessage(createdAt: Instant?): GroupMessageModel =
        GroupMessageModel().apply {
            this.uid = "earliest-incoming-content-message"
            this.createdAt = createdAt
        }
}
