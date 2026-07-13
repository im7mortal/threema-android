package ch.threema.app.poll

import ch.threema.app.eventbus.events.PollEvent
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.services.poll.PollService
import ch.threema.app.test.koinTestModuleRule
import ch.threema.domain.types.Identity
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType
import ch.threema.storage.models.group.GroupModelOld
import ch.threema.storage.models.poll.GroupPollModel
import ch.threema.storage.models.poll.PollModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testdata.TestData.Identities.ME
import testdata.TestData.Identities.OTHER_1
import testdata.TestData.Identities.OTHER_2
import testdata.TestData.Identities.OTHER_3

@OptIn(ExperimentalCoroutinesApi::class)
class PollGroupStatusMonitorTest {

    private lateinit var pollModelMock: PollModel
    private lateinit var pollEvents: MutableSharedFlow<PollEvent>
    private lateinit var pollServiceMock: PollService
    private lateinit var groupModelMock: GroupModelOld
    private lateinit var groupServiceMock: GroupService
    private lateinit var messageServiceMock: MessageService
    private lateinit var pollGroupStatusMonitor: PollGroupStatusMonitor

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory<PollService> { pollServiceMock }
        factory<GroupService> { groupServiceMock }
        factory<MessageService> { messageServiceMock }
    }

    @BeforeTest
    fun setUp() {
        pollModelMock = mockk<PollModel> {
            every { id } returns POLL_ID
            every { name } returns POLL_NAME
        }
        pollEvents = MutableSharedFlow()
        pollServiceMock = mockk {
            every { getLinkedPollModel(pollModelMock) } returns mockk<GroupPollModel> {
                every { groupId } returns GROUP_ID
            }
        }
        groupModelMock = mockk()
        groupServiceMock = mockk {
            every { getById(GROUP_ID) } returns groupModelMock
            every { createReceiver(groupModelMock) } returns receiverMock
        }
        messageServiceMock = mockk {
            every { createGroupStatus(receiverMock, any(), any(), any(), null) } returns mockk()
        }
        pollGroupStatusMonitor = PollGroupStatusMonitor(
            globalEventFlows = mockk {
                every { polls } returns pollEvents
            },
            identityProvider = mockk {
                every { getIdentityString() } returns ME.value
            },
        )
    }

    @Test
    fun `status messages for votes are created for poll type INTERMEDIATE where I am the creator`() = runTest {
        every { pollModelMock.type } returns PollModel.Type.INTERMEDIATE
        every { pollModelMock.creatorIdentity } returns ME.value
        every { pollServiceMock.getPendingParticipants(POLL_ID) } returns listOf(OTHER_3.value)
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            pollGroupStatusMonitor.run()
        }

        emitPollVoteEvent(OTHER_1, isNewVote = true)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.FIRST_VOTE, OTHER_1)

        emitPollVoteEvent(OTHER_1, isNewVote = false)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.MODIFIED_VOTE, OTHER_1)

        emitPollVoteEvent(OTHER_2, isNewVote = true)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.FIRST_VOTE, OTHER_2)

        verifyGroupStatusCreated(exactly = 0, type = GroupStatusType.VOTES_COMPLETE)

        every { pollServiceMock.getPendingParticipants(POLL_ID) } returns emptyList()
        emitPollVoteEvent(OTHER_3, isNewVote = true)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.VOTES_COMPLETE)

        // Changing a vote after all votes have been collected does not create a status message
        emitPollVoteEvent(OTHER_3, isNewVote = false)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.VOTES_COMPLETE)

        monitorJob.cancel()
    }

    @Test
    fun `no status messages for votes are created for poll type INTERMEDIATE where I am not the creator`() = runTest {
        every { pollModelMock.type } returns PollModel.Type.INTERMEDIATE
        every { pollModelMock.creatorIdentity } returns OTHER_1.value
        every { pollServiceMock.getPendingParticipants(POLL_ID) } returns listOf(OTHER_3.value)
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            pollGroupStatusMonitor.run()
        }

        emitPollVoteEvent(OTHER_1, isNewVote = true)
        verifyGroupStatusCreated(exactly = 0, type = GroupStatusType.FIRST_VOTE, OTHER_1)

        emitPollVoteEvent(OTHER_1, isNewVote = false)
        verifyGroupStatusCreated(exactly = 0, type = GroupStatusType.MODIFIED_VOTE, OTHER_1)

        emitPollVoteEvent(OTHER_2, isNewVote = true)
        verifyGroupStatusCreated(exactly = 0, type = GroupStatusType.FIRST_VOTE, OTHER_2)

        verifyGroupStatusCreated(exactly = 0, type = GroupStatusType.VOTES_COMPLETE)

        every { pollServiceMock.getPendingParticipants(POLL_ID) } returns emptyList()
        emitPollVoteEvent(OTHER_3, isNewVote = true)
        verifyGroupStatusCreated(exactly = 0, type = GroupStatusType.VOTES_COMPLETE)

        emitPollVoteEvent(OTHER_3, isNewVote = false)
        verifyGroupStatusCreated(exactly = 0, type = GroupStatusType.VOTES_COMPLETE)

        monitorJob.cancel()
    }

    @Test
    fun `status messages for votes are created for poll type RESULT_ON_CLOSE where I am the creator`() = runTest {
        every { pollModelMock.type } returns PollModel.Type.RESULT_ON_CLOSE
        every { pollModelMock.creatorIdentity } returns ME.value
        every { pollServiceMock.getPendingParticipants(POLL_ID) } returns listOf(OTHER_3.value)
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            pollGroupStatusMonitor.run()
        }

        emitPollVoteEvent(OTHER_1, isNewVote = true)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.RECEIVED_VOTE, OTHER_1)

        // Changing a vote does not result in a new status message
        emitPollVoteEvent(OTHER_1, isNewVote = false)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.RECEIVED_VOTE, OTHER_1)

        emitPollVoteEvent(OTHER_2, isNewVote = true)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.RECEIVED_VOTE, OTHER_2)

        verifyGroupStatusCreated(exactly = 0, type = GroupStatusType.VOTES_COMPLETE)

        every { pollServiceMock.getPendingParticipants(POLL_ID) } returns emptyList()
        emitPollVoteEvent(OTHER_3, isNewVote = true)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.VOTES_COMPLETE)

        monitorJob.cancel()
    }

    @Test
    fun `no status messages for votes are created for poll type RESULT_ON_CLOSE where I am not the creator`() = runTest {
        every { pollModelMock.type } returns PollModel.Type.RESULT_ON_CLOSE
        every { pollModelMock.creatorIdentity } returns OTHER_1.value
        every { pollServiceMock.getPendingParticipants(POLL_ID) } returns listOf(OTHER_3.value)
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            pollGroupStatusMonitor.run()
        }

        emitPollVoteEvent(OTHER_1, isNewVote = true)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.RECEIVED_VOTE, OTHER_1)

        // Changing a vote does not result in a new status message
        emitPollVoteEvent(OTHER_1, isNewVote = false)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.RECEIVED_VOTE, OTHER_1)

        emitPollVoteEvent(OTHER_2, isNewVote = true)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.RECEIVED_VOTE, OTHER_2)

        verifyGroupStatusCreated(exactly = 0, type = GroupStatusType.VOTES_COMPLETE)

        every { pollServiceMock.getPendingParticipants(POLL_ID) } returns emptyList()
        emitPollVoteEvent(OTHER_3, isNewVote = true)
        verifyGroupStatusCreated(exactly = 1, type = GroupStatusType.VOTES_COMPLETE)

        monitorJob.cancel()
    }

    private suspend fun emitPollVoteEvent(voterIdentity: Identity, isNewVote: Boolean) {
        pollEvents.emit(PollEvent.PollVoted(pollModelMock, voterIdentity, isNewVote))
    }

    private fun verifyGroupStatusCreated(
        exactly: Int,
        type: GroupStatusType,
        identity: Identity? = null,
    ) {
        verify(exactly = exactly) {
            messageServiceMock.createGroupStatus(
                receiverMock,
                type,
                identity?.value,
                POLL_NAME,
                null,
            )
        }
    }

    companion object {
        private const val POLL_ID = 123
        private const val POLL_NAME = "My Poll"
        private const val GROUP_ID = 42
        private val receiverMock = mockk<GroupMessageReceiver>()
    }
}
