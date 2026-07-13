package ch.threema.app.conversation

import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.test.koinTestModuleRule
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupState
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType
import ch.threema.testhelpers.unconfinedTestDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testdata.TestData.Identities.OTHER_1
import testdata.TestData.Identities.OTHER_2
import testdata.TestData.Identities.OTHER_3
import testdata.TestData.Identities.OTHER_4

@OptIn(ExperimentalCoroutinesApi::class)
class GroupStatusMessageMonitorTest {

    private lateinit var receiverMock: GroupMessageReceiver
    private lateinit var messageServiceMock: MessageService
    private lateinit var groupServiceMock: GroupService

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        receiverMock = mockk()
        factory<MessageService> { messageServiceMock }
        factory<GroupService> { groupServiceMock }
    }

    @BeforeTest
    fun setUp() {
        messageServiceMock = mockk(relaxed = true)
        groupServiceMock = mockk {
            every { createReceiver(groupIdentity) } returns receiverMock
        }
    }

    @Test
    fun `group status messages are created for relevant group events`() = runTest {
        val testDispatcher = unconfinedTestDispatcherProvider()
        val groupEvents = MutableSharedFlow<GroupEvent>()
        val groupStatusMessageMonitor = GroupStatusMessageMonitor(
            globalEventFlows = mockk {
                every { groups } returns groupEvents
            },
            dispatcherProvider = testDispatcher,
        )
        val monitorJob = launch(testDispatcher.worker) {
            groupStatusMessageMonitor.run()
        }

        // GroupUpdated events do not lead to group status messages
        groupEvents.emit(GroupEvent.GroupUpdated(groupIdentity))
        verify(exactly = 0) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                any(),
                any(),
                any(),
                any(),
            )
        }

        groupEvents.emit(GroupEvent.NewGroup(groupIdentity))
        verify(exactly = 1) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                GroupStatusType.CREATED,
                null,
                null,
                null,
            )
        }

        // New members, profile picture updates or renames immediately after group creation are ignored
        groupEvents.emit(GroupEvent.NewMember(groupIdentity, OTHER_3))
        groupEvents.emit(GroupEvent.NewMember(groupIdentity, OTHER_4))
        groupEvents.emit(GroupEvent.GroupProfilePictureUpdated(groupIdentity))
        groupEvents.emit(GroupEvent.GroupRenamed(groupIdentity, newName = "Our group"))
        verify(exactly = 0) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                match { it == GroupStatusType.MEMBER_ADDED || it == GroupStatusType.PROFILE_PICTURE_UPDATED || it == GroupStatusType.RENAMED },
                any(),
                null,
                null,
            )
        }

        // Members added significantly after group creation result in a group status message
        delay(6.seconds)
        groupEvents.emit(GroupEvent.NewMember(groupIdentity, OTHER_1))
        verify(exactly = 1) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                GroupStatusType.MEMBER_ADDED,
                OTHER_1.value,
                null,
                null,
            )
        }

        groupEvents.emit(GroupEvent.MemberKicked(groupIdentity, OTHER_1))
        verify(exactly = 1) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                GroupStatusType.MEMBER_KICKED,
                OTHER_1.value,
                null,
                null,
            )
        }

        groupEvents.emit(GroupEvent.MemberLeft(groupIdentity, OTHER_2))
        verify(exactly = 1) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                GroupStatusType.MEMBER_LEFT,
                OTHER_2.value,
                null,
                null,
            )
        }

        groupEvents.emit(GroupEvent.GroupProfilePictureUpdated(groupIdentity))
        verify(exactly = 1) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                GroupStatusType.PROFILE_PICTURE_UPDATED,
                null,
                null,
                null,
            )
        }

        groupEvents.emit(GroupEvent.GroupRenamed(groupIdentity, newName = "New name"))
        verify(exactly = 1) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                GroupStatusType.RENAMED,
                null,
                null,
                "New name",
            )
        }

        groupEvents.emit(GroupEvent.GroupStateChanged(groupIdentity, newState = GroupState.NOTES))
        verify(exactly = 1) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                GroupStatusType.IS_NOTES_GROUP,
                null,
                null,
                null,
            )
        }

        groupEvents.emit(GroupEvent.GroupStateChanged(groupIdentity, newState = GroupState.PEOPLE))
        verify(exactly = 1) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                GroupStatusType.IS_PEOPLE_GROUP,
                null,
                null,
                null,
            )
        }

        monitorJob.cancel()
    }

    companion object {
        private val groupIdentity = mockk<GroupIdentity>()
    }
}
