package ch.threema.app.processors.reflectedd2dsync

import ch.threema.app.eventbus.EventBus
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupState
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.domain.models.UserState
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.d2d.GroupSync
import ch.threema.protobuf.d2d.sync.Group
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import testdata.TestData.Identities

class ReflectedGroupSyncTaskTest {

    private lateinit var groupModelMock: GroupModel
    private lateinit var groupModelData: GroupModelData
    private lateinit var groupSyncMock: GroupSync
    private lateinit var groupSyncUpdateMock: GroupSync.Update
    private lateinit var groupSyncGroupMock: Group
    private lateinit var memberStateChangeMock: Map<IdentityString, GroupSync.Update.MemberStateChange>
    private lateinit var groupEventBusMock: EventBus<GroupEvent>
    private lateinit var reflectedGroupSyncTask: ReflectedGroupSyncTask

    @BeforeTest
    fun setUp() {
        groupModelData = GroupModelData(
            groupIdentity = GROUP_IDENTITY,
            name = "",
            createdAt = mockk(),
            synchronizedAt = null,
            lastUpdate = null,
            conversationVisibility = ConversationVisibility.NORMAL,
            groupDescription = null,
            groupDescriptionChangedAt = null,
            otherMembers = emptySet(),
            userState = UserState.MEMBER,
            notificationTriggerPolicyOverride = null,
        )
        groupModelMock = mockk<GroupModel> {
            every { groupIdentity } returns GROUP_IDENTITY
            every { setMembersFromSync(any()) } just runs
            every { data } answers { groupModelData }
        }
        groupSyncGroupMock = mockk<Group> {
            every { groupIdentity } returns mockk {
                every { creatorIdentity } returns Identities.ME.value
                every { groupId } returns GROUP_ID
            }
            every { hasName() } returns false
            every { hasUserState() } returns false
            every { hasNotificationTriggerPolicyOverride() } returns false
            every { hasProfilePicture() } returns false
            every { hasMemberIdentities() } returns false
            every { hasConversationCategory() } returns false
            every { hasConversationVisibility() } returns false
        }
        memberStateChangeMock = emptyMap()
        groupSyncUpdateMock = mockk<GroupSync.Update> {
            every { group } returns groupSyncGroupMock
            every { memberStateChangesMap } answers { memberStateChangeMock }
        }
        groupSyncMock = mockk<GroupSync> {
            every { actionCase } returns GroupSync.ActionCase.UPDATE
            every { update } returns groupSyncUpdateMock
        }
        groupEventBusMock = mockk(relaxed = true)

        reflectedGroupSyncTask = ReflectedGroupSyncTask(
            groupSync = groupSyncMock,
            groupModelRepository = mockk {
                every { getByGroupIdentity(GROUP_IDENTITY) } returns groupModelMock
            },
            groupService = mockk(),
            fileService = mockk(),
            okHttpClient = mockk(),
            serverAddressProvider = mockk(),
            symmetricEncryptionService = mockk(),
            multiDeviceManager = mockk(),
            conversationCategoryService = mockk(),
            globalEventBuses = mockk {
                every { groups } returns groupEventBusMock
            },
            userService = mockk {
                every { identity } returns Identities.ME.value
            },
        )
    }

    @Test
    fun `group state change is emitted on event bus when members are added to notes group`() {
        every { groupModelMock.getGroupState() } returns GroupState.NOTES
        every { groupSyncGroupMock.hasMemberIdentities() } returns true
        every { groupSyncGroupMock.memberIdentities } returns mockk {
            every { identitiesList } returns listOf(
                Identities.OTHER_1.value,
                Identities.OTHER_2.value,
            )
        }
        memberStateChangeMock = mapOf(
            Identities.OTHER_1.value to GroupSync.Update.MemberStateChange.ADDED,
            Identities.OTHER_2.value to GroupSync.Update.MemberStateChange.ADDED,
        )
        every { groupModelMock.setMembersFromSync(setOf(Identities.OTHER_1.value, Identities.OTHER_2.value)) } answers {
            every { groupModelMock.getGroupState() } returns GroupState.PEOPLE
        }

        reflectedGroupSyncTask.run()

        verify {
            groupEventBusMock.emit(
                GroupEvent.NewMember(GROUP_IDENTITY, Identities.OTHER_1),
            )
            groupEventBusMock.emit(
                GroupEvent.NewMember(GROUP_IDENTITY, Identities.OTHER_2),
            )
            groupEventBusMock.emit(
                GroupEvent.GroupStateChanged(GROUP_IDENTITY, newState = GroupState.PEOPLE),
            )
        }
    }

    @Test
    fun `group state change is emitted on event bus when all members are gone from a group`() {
        groupModelData = groupModelData.copy(
            otherMembers = setOf(Identities.OTHER_1.value, Identities.OTHER_2.value),
        )
        every { groupModelMock.getGroupState() } returns GroupState.PEOPLE
        every { groupSyncGroupMock.hasMemberIdentities() } returns true
        every { groupSyncGroupMock.memberIdentities } returns mockk {
            every { identitiesList } returns emptyList()
        }
        memberStateChangeMock = mapOf(
            Identities.OTHER_1.value to GroupSync.Update.MemberStateChange.LEFT,
            Identities.OTHER_2.value to GroupSync.Update.MemberStateChange.KICKED,
        )
        every { groupModelMock.setMembersFromSync(emptySet()) } answers {
            every { groupModelMock.getGroupState() } returns GroupState.NOTES
        }

        reflectedGroupSyncTask.run()

        verify {
            groupEventBusMock.emit(
                GroupEvent.MemberLeft(GROUP_IDENTITY, Identities.OTHER_1),
            )
            groupEventBusMock.emit(
                GroupEvent.MemberKicked(GROUP_IDENTITY, Identities.OTHER_2),
            )
            groupEventBusMock.emit(
                GroupEvent.GroupStateChanged(GROUP_IDENTITY, newState = GroupState.NOTES),
            )
        }
    }

    companion object {
        private const val GROUP_ID = 123L
        private val GROUP_IDENTITY = GroupIdentity(Identities.ME.value, GROUP_ID)
    }
}
