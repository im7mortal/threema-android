package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.conversion.toLibthreemaGroup
import ch.threema.app.libthreema.conversion.toLibthreemaGroupIdentity
import ch.threema.app.libthreema.extensions.persistLibthreemaGroupProfilePicture
import ch.threema.app.services.FileService
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.UserState
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.ConversationCategory as LibthreemaConversationCategory
import ch.threema.libthreema.ConversationVisibility as LibthreemaConversationVisibility
import ch.threema.libthreema.Group as LibthreemaGroup
import ch.threema.libthreema.GroupMembershipFilter as LibthreemaGroupMembershipFilter
import ch.threema.libthreema.GroupWithProfilePicture
import ch.threema.libthreema.ProfilePicture as LibthreemaProfilePicture
import ch.threema.libthreema.ProfilePictureMediaType as LibthreemaProfilePictureMediaType
import ch.threema.libthreema.ProviderException as LibthreemaProviderException
import ch.threema.libthreema.UserState as LibthreemaUserState
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.utcDate
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import testdata.TestData.GroupIdentities
import testdata.TestData.Identities
import testdata.TestData.createGroupModel

class GroupProviderTest {

    private val userIdentity = Identities.ME
    private val guaranteedIdentityProvider = GuaranteedIdentityProvider(
        identityProvider = TestIdentityProvider(userIdentity),
    )

    @Test
    fun `isPartOfAnyGroup fails for user's identity`() {
        val contactProvider = createGroupProvider()
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.isPartOfAnyGroup(
                identity = userIdentity.value,
                filter = LibthreemaGroupMembershipFilter.ANY,
            )
        }

        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            contactProvider.isPartOfAnyGroup(
                identity = userIdentity.value,
                filter = LibthreemaGroupMembershipFilter.ONLY_ACTIVE,
            )
        }
    }

    @Test
    fun `isPartOfAnyGroup applies filter correctly`() {
        // Arrange
        val groupModelRepository = mockk<GroupModelRepository> {
            every { isPartOfAnyGroup(any()) } returns true
            every { isPartOfAnyGroupWithUserState(any(), any()) } returns true
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
        )

        // Act
        groupProvider.isPartOfAnyGroup(
            identity = Identities.OTHER_1.value,
            filter = LibthreemaGroupMembershipFilter.ANY,
        )
        groupProvider.isPartOfAnyGroup(
            identity = Identities.OTHER_2.value,
            filter = LibthreemaGroupMembershipFilter.ONLY_ACTIVE,
        )

        // Assert
        verify(exactly = 1) {
            groupModelRepository.isPartOfAnyGroup(
                contactIdentity = Identities.OTHER_1.value,
            )
        }
        verify(exactly = 1) {
            groupModelRepository.isPartOfAnyGroupWithUserState(
                contactIdentity = Identities.OTHER_2.value,
                userState = UserState.MEMBER,
            )
        }
    }

    @Test
    fun `getIdentities returns all identities`() {
        // arrange
        val groupIdentities = setOf(
            GroupIdentities.GROUP_1,
            GroupIdentities.GROUP_2,
            GroupIdentities.GROUP_ME,
        )

        val groupModelRepositoryMock: GroupModelRepository = mockk {
            every { getAllGroupIdentities() } returns groupIdentities
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepositoryMock,
        )

        // act
        val allGroupIdentities = groupProvider.getIdentities()

        // assert
        assertEquals(
            expected = groupIdentities.map(GroupIdentity::toLibthreemaGroupIdentity).toSet(),
            actual = allGroupIdentities,
        )
    }

    @Test
    fun `getWithProfilePicture returns null if group does not exist`() {
        // arrange
        val groupIdentity = GroupIdentities.GROUP_1
        val groupModelRepositoryMock: GroupModelRepository = mockk {
            every { getByGroupIdentity(groupIdentity) } returns null
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepositoryMock,
        )

        // act
        val groupWithProfilePicture = groupProvider.getWithProfilePicture(groupIdentity.toLibthreemaGroupIdentity())

        // assert
        assertNull((groupWithProfilePicture))
    }

    @Test
    fun `getWithProfilePicture returns group without profile picture if no profile picture exists`() {
        // arrange
        val groupModel = createGroupModel(groupIdentity = GroupIdentities.GROUP_1)
        val groupModelRepository = mockk<GroupModelRepository> {
            every { getByGroupIdentity(GroupIdentities.GROUP_1) } returns groupModel
        }
        val fileService = mockk<FileService> {
            every { getGroupProfilePictureBytes(groupModel.getDatabaseId()) } returns null
        }

        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            fileService = fileService,
        )

        // act
        val result = groupProvider.getWithProfilePicture(GroupIdentities.GROUP_1.toLibthreemaGroupIdentity())

        // assert
        assertEquals(
            expected = GroupWithProfilePicture(
                group = groupModel.data!!.toLibthreemaGroup(),
                profilePicture = null,
            ),
            actual = result,
        )
    }

    @Test
    fun `getWithProfilePicture returns group with profile picture`() {
        // arrange
        val groupIdentity = GroupIdentities.GROUP_1
        val groupModel = createGroupModel(groupIdentity = groupIdentity)
        val profilePicture = LibthreemaProfilePicture(
            mediaType = LibthreemaProfilePictureMediaType.JPEG,
            data = byteArrayOf(1, 2, 3, 4),
        )
        val groupModelRepository = mockk<GroupModelRepository> {
            every { getByGroupIdentity(groupIdentity) } returns groupModel
        }
        val fileService = mockk<FileService> {
            every { getGroupProfilePictureBytes(groupModel.getDatabaseId()) } returns profilePicture.data
        }

        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            fileService = fileService,
        )

        // act
        val result = groupProvider.getWithProfilePicture(groupIdentity.toLibthreemaGroupIdentity())

        // assert
        assertEquals(
            expected = GroupWithProfilePicture(
                group = groupModel.data!!.toLibthreemaGroup(),
                profilePicture = profilePicture,
            ),
            actual = result,
        )
    }

    @Test
    fun `addWithProfilePicture fails when group with identity already exists`() {
        // arrange
        val groupIdentities = setOf(
            GroupIdentity(
                groupId = 1L,
                creatorIdentity = Identities.OTHER_1.value,
            ),
            GroupIdentity(
                groupId = 2L,
                creatorIdentity = Identities.OTHER_2.value,
            ),
        )
        val groupModelRepository = mockk<GroupModelRepository> {
            every { existsAnyByGroupIdentity(groupIdentities) } returns true
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
        )

        // act / assert
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            groupProvider.addWithProfilePicture(
                groups = listOf(
                    GroupWithProfilePicture(
                        group = createLibthreemaGroup(
                            groupIdentity = groupIdentities.first(),
                            otherMembers = emptySet(),
                        ),
                        profilePicture = null,
                    ),
                    GroupWithProfilePicture(
                        group = createLibthreemaGroup(
                            groupIdentity = groupIdentities.last(),
                            otherMembers = emptySet(),
                        ),
                        profilePicture = null,
                    ),
                ),
            )
        }

        verify(exactly = 1) {
            groupModelRepository.existsAnyByGroupIdentity(groupIdentities)
        }
        verify(exactly = 0) {
            groupModelRepository.persistNewGroup(any())
        }
    }

    @Test
    fun `addWithProfilePicture fails if a creator does not exist as a contact`() {
        // arrange
        val groupModelRepository = mockk<GroupModelRepository> {
            every { existsAnyByGroupIdentity(any()) } returns false
        }
        val contactModelRepository = mockk<ContactModelRepository> {
            every {
                countExisting(
                    setOf(
                        Identities.OTHER_1.value,
                        Identities.OTHER_2.value,
                        Identities.OTHER_3.value,
                        Identities.OTHER_4.value,
                    ),
                )
            } returns 3u
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            contactModelRepository = contactModelRepository,
        )

        // act / assert
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            groupProvider.addWithProfilePicture(
                groups = listOf(
                    GroupWithProfilePicture(
                        group = createLibthreemaGroup(
                            groupIdentity = GroupIdentity(
                                groupId = 1L,
                                creatorIdentity = Identities.OTHER_1.value,
                            ),
                            otherMembers = setOf(
                                Identities.OTHER_2.value,
                            ),
                        ),
                        profilePicture = null,
                    ),
                    GroupWithProfilePicture(
                        group = createLibthreemaGroup(
                            groupIdentity = GroupIdentity(
                                groupId = 2L,
                                creatorIdentity = Identities.OTHER_3.value,
                            ),
                            otherMembers = setOf(
                                Identities.OTHER_4.value,
                            ),
                        ),
                        profilePicture = null,
                    ),
                ),
            )
        }

        verify(exactly = 1) {
            contactModelRepository.countExisting(
                setOf(
                    Identities.OTHER_1.value,
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                    Identities.OTHER_4.value,
                ),
            )
        }
        confirmVerified(contactModelRepository)
        verify(exactly = 0) { groupModelRepository.persistNewGroup(any()) }
    }

    @Test
    fun `addWithProfilePicture fails if a member does not exist as a contact`() {
        // arrange
        val groupModelRepository = mockk<GroupModelRepository> {
            every { existsAnyByGroupIdentity(any()) } returns false
        }
        val contactModelRepository = mockk<ContactModelRepository> {
            every {
                countExisting(
                    setOf(
                        Identities.OTHER_1.value,
                        Identities.OTHER_2.value,
                        Identities.OTHER_3.value,
                        Identities.OTHER_4.value,
                    ),
                )
            } returns 3u
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            contactModelRepository = contactModelRepository,
        )

        // act / assert
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            groupProvider.addWithProfilePicture(
                groups = listOf(
                    GroupWithProfilePicture(
                        group = createLibthreemaGroup(
                            groupIdentity = GroupIdentity(
                                groupId = 1L,
                                creatorIdentity = Identities.OTHER_1.value,
                            ),
                            otherMembers = setOf(
                                Identities.OTHER_2.value,
                            ),
                        ),
                        profilePicture = null,
                    ),
                    GroupWithProfilePicture(
                        group = createLibthreemaGroup(
                            groupIdentity = GroupIdentity(
                                groupId = 2L,
                                creatorIdentity = Identities.OTHER_3.value,
                            ),
                            otherMembers = setOf(
                                Identities.OTHER_4.value,
                            ),
                        ),
                        profilePicture = null,
                    ),
                ),
            )
        }

        verify(exactly = 1) {
            contactModelRepository.countExisting(
                setOf(
                    Identities.OTHER_1.value,
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                    Identities.OTHER_4.value,
                ),
            )
        }
        confirmVerified(contactModelRepository)
        verify(exactly = 0) { groupModelRepository.persistNewGroup(any()) }
    }

    @Test
    fun `addWithProfilePicture ignores user identity when checking contacts`() {
        // arrange
        val fileService = mockk<FileService> {
            every { removeGroupProfilePicture(any()) } just runs
        }
        val groupModelRepository = mockk<GroupModelRepository> {
            every { existsAnyByGroupIdentity(any()) } returns false
            every { persistNewGroups(any()) } returns listOf(
                createGroupModel(
                    groupIdentity = GroupIdentities.GROUP_ME,
                ),
            )
        }
        val contactModelRepository = mockk<ContactModelRepository> {
            every {
                countExisting(
                    setOf(
                        Identities.OTHER_1.value,
                    ),
                )
            } returns 1u
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            contactModelRepository = contactModelRepository,
            fileService = fileService,
        )

        // act
        groupProvider.addWithProfilePicture(
            groups = listOf(
                GroupWithProfilePicture(
                    group = createLibthreemaGroup(
                        groupIdentity = GroupIdentities.GROUP_ME,
                        otherMembers = setOf(
                            Identities.OTHER_1.value,
                        ),
                    ),
                    profilePicture = null,
                ),
            ),
        )

        // assert
        verify(exactly = 1) {
            contactModelRepository.countExisting(
                setOf(
                    Identities.OTHER_1.value,
                ),
            )
        }
        confirmVerified(contactModelRepository)
    }

    @Test
    fun `addWithProfilePicture persists all groups and profile pictures`() {
        // arrange
        val contactModelRepository = mockk<ContactModelRepository> {
            every { countExisting(any()) } returns 4u
        }
        val newGroupModels = listOf(
            createGroupModel(
                groupIdentity = GroupIdentities.GROUP_ME,
                otherMembers = setOf(
                    Identities.OTHER_1.value,
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                    Identities.OTHER_4.value,
                ),
                userState = UserState.MEMBER,
            ),
            createGroupModel(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = emptySet(),
                userState = UserState.KICKED,
            ),
            createGroupModel(
                groupIdentity = GroupIdentities.GROUP_2,
                otherMembers = emptySet(),
                userState = UserState.LEFT,
            ),
        )
        val groupModelRepository = mockk<GroupModelRepository> {
            every { existsAnyByGroupIdentity(any()) } returns false
            every { persistNewGroups(any()) } returns newGroupModels
        }
        val fileService = mockk<FileService> {
            every { writeGroupProfilePicture(any(), any<ByteArray>()) } just runs
            every { removeGroupProfilePicture(any()) } just runs
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            contactModelRepository = contactModelRepository,
            fileService = fileService,
        )

        // act
        groupProvider.addWithProfilePicture(
            groups = listOf(
                GroupWithProfilePicture(
                    group = createLibthreemaGroup(
                        groupIdentity = GroupIdentities.GROUP_ME,
                        otherMembers = setOf(
                            Identities.OTHER_1.value,
                            Identities.OTHER_2.value,
                            Identities.OTHER_3.value,
                            Identities.OTHER_4.value,
                        ),
                        userState = LibthreemaUserState.MEMBER,
                    ),
                    profilePicture = null,
                ),
                GroupWithProfilePicture(
                    group = createLibthreemaGroup(
                        groupIdentity = GroupIdentities.GROUP_1,
                        otherMembers = emptySet(),
                        userState = LibthreemaUserState.KICKED,
                    ),
                    profilePicture = null,
                ),
                GroupWithProfilePicture(
                    group = createLibthreemaGroup(
                        groupIdentity = GroupIdentities.GROUP_2,
                        otherMembers = emptySet(),
                        userState = LibthreemaUserState.LEFT,
                    ),
                    profilePicture = LibthreemaProfilePicture(
                        mediaType = LibthreemaProfilePictureMediaType.JPEG,
                        data = byteArrayOf(0, 0, 0, 0),
                    ),
                ),
            ),
        )

        // assert
        verify(exactly = 1) {
            groupModelRepository.persistNewGroups(
                groups = newGroupModels.mapNotNull(GroupModel::data).toSet(),
            )
        }
        verify(exactly = 1) {
            fileService.removeGroupProfilePicture(newGroupModels[0])
        }
        verify(exactly = 1) {
            fileService.removeGroupProfilePicture(newGroupModels[1])
        }
        verify(exactly = 1) {
            fileService.persistLibthreemaGroupProfilePicture(
                groupModel = newGroupModels[2],
                profilePicture = LibthreemaProfilePicture(
                    mediaType = LibthreemaProfilePictureMediaType.JPEG,
                    data = byteArrayOf(0, 0, 0, 0),
                ),
            )
        }
    }

    @Test
    fun `addWithProfilePicture persists no groups for an empty list`() {
        // arrange
        val groupModelRepository = mockk<GroupModelRepository> {
            every { existsAnyByGroupIdentity(emptySet()) } returns false
            every { persistNewGroups(emptySet()) } returns emptyList()
        }
        val contactModelRepository = mockk<ContactModelRepository> {
            every { countExisting(emptySet()) } returns 0u
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            contactModelRepository = contactModelRepository,
        )

        // act
        groupProvider.addWithProfilePicture(groups = emptyList())

        // assert
        verify(exactly = 1) { groupModelRepository.persistNewGroups(emptySet()) }
    }

    @Test
    fun `setProfilePicture fails if group does not exist`() {
        // arrange
        val groupIdentity = GroupIdentities.GROUP_1
        val groupModelRepository = mockk<GroupModelRepository> {
            every { getByGroupIdentity(groupIdentity) } returns null
        }
        val fileService = mockk<FileService>()
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            fileService = fileService,
        )

        // act / assert
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            groupProvider.setProfilePicture(
                identity = groupIdentity.toLibthreemaGroupIdentity(),
                profilePicture = null,
            )
        }

        confirmVerified(fileService)
    }

    @Test
    fun `setProfilePicture sets the profile picture for a group`() {
        // arrange
        val groupIdentity = GroupIdentities.GROUP_1
        val groupModel = createGroupModel(groupIdentity = groupIdentity)
        val profilePictureBytes = byteArrayOf(1, 2, 3)
        val profilePicture = LibthreemaProfilePicture(
            mediaType = LibthreemaProfilePictureMediaType.JPEG,
            data = profilePictureBytes,
        )
        val groupModelRepository = mockk<GroupModelRepository> {
            every { getByGroupIdentity(groupIdentity) } returns groupModel
        }
        val fileService = mockk<FileService> {
            every { writeGroupProfilePicture(groupModel, profilePictureBytes) } just runs
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            fileService = fileService,
        )

        // act
        groupProvider.setProfilePicture(
            identity = groupIdentity.toLibthreemaGroupIdentity(),
            profilePicture = profilePicture,
        )

        // assert
        verify(exactly = 1) {
            fileService.persistLibthreemaGroupProfilePicture(
                groupModel = groupModel,
                profilePicture = profilePicture,
            )
        }
    }

    @Test
    fun `setProfilePicture deletes the profile picture for a group if it is null`() {
        // arrange
        val groupIdentity = GroupIdentities.GROUP_1
        val groupModel = createGroupModel(groupIdentity = groupIdentity)
        val groupModelRepository = mockk<GroupModelRepository> {
            every { getByGroupIdentity(groupIdentity) } returns groupModel
        }
        val fileService = mockk<FileService> {
            every { removeGroupProfilePicture(groupModel) } just runs
        }
        val groupProvider = createGroupProvider(
            groupModelRepository = groupModelRepository,
            fileService = fileService,
        )

        // act
        groupProvider.setProfilePicture(
            identity = groupIdentity.toLibthreemaGroupIdentity(),
            profilePicture = null,
        )

        // assert
        verify(exactly = 1) {
            fileService.persistLibthreemaGroupProfilePicture(
                groupModel = groupModel,
                profilePicture = null,
            )
        }
    }

    private fun createLibthreemaGroup(
        groupIdentity: GroupIdentity,
        otherMembers: Set<IdentityString>,
        userState: LibthreemaUserState = LibthreemaUserState.MEMBER,
    ) = LibthreemaGroup(
        identity = groupIdentity.toLibthreemaGroupIdentity(),
        name = "Group",
        createdAt = utcDate(2026, 7, 21).toEpochMilli().toULong(),
        lastUpdateAt = null,
        userState = userState,
        members = otherMembers,
        notificationTriggerPolicyOverride = null,
        conversationCategory = LibthreemaConversationCategory.DEFAULT,
        conversationVisibility = LibthreemaConversationVisibility.NORMAL,
    )

    private fun createGroupProvider(
        contactModelRepository: ContactModelRepository = mockk(),
        groupModelRepository: GroupModelRepository = mockk(),
        fileService: FileService = mockk(),
        guaranteedIdentityProvider: GuaranteedIdentityProvider = this.guaranteedIdentityProvider,
    ) = GroupProvider(
        contactModelRepository = contactModelRepository,
        groupModelRepository = groupModelRepository,
        fileService = fileService,
        guaranteedIdentityProvider = guaranteedIdentityProvider,
    )
}
