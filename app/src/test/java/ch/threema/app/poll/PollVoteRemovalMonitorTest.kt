package ch.threema.app.poll

import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.services.GroupService
import ch.threema.app.services.poll.PollService
import ch.threema.app.test.koinTestModuleRule
import ch.threema.data.datatypes.GroupIdentity
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
import testdata.TestData.Identities.OTHER_1
import testdata.TestData.Identities.OTHER_2
import testdata.TestData.Identities.OTHER_3

@OptIn(ExperimentalCoroutinesApi::class)
class PollVoteRemovalMonitorTest {

    private lateinit var receiverMock: GroupMessageReceiver
    private lateinit var pollServiceMock: PollService
    private lateinit var groupServiceMock: GroupService

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory<PollService> { pollServiceMock }
        factory<GroupService> { groupServiceMock }
    }

    @BeforeTest
    fun setUp() {
        receiverMock = mockk()
        pollServiceMock = mockk(relaxed = true)
        groupServiceMock = mockk {
            every { createReceiver(groupIdentity) } returns receiverMock
        }
    }

    @Test
    fun `votes are deleted when members leave group`() = runTest {
        val groupEvents = MutableSharedFlow<GroupEvent>()
        val pollVoteRemovalMonitor = PollVoteRemovalMonitor(
            globalEventFlows = mockk {
                every { groups } returns groupEvents
            },
        )
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            pollVoteRemovalMonitor.run()
        }

        // Votes are not removed for new members
        groupEvents.emit(GroupEvent.NewMember(groupIdentity, identity = OTHER_1))
        verify(exactly = 0) {
            pollServiceMock.removeVotes(receiverMock, any())
        }

        // Votes are removed when a member is kicked
        groupEvents.emit(GroupEvent.MemberKicked(groupIdentity, identity = OTHER_2))
        verify(exactly = 1) {
            pollServiceMock.removeVotes(receiverMock, OTHER_2.value)
        }

        // Votes are removed when a member leaves
        groupEvents.emit(GroupEvent.MemberLeft(groupIdentity, identity = OTHER_3))
        verify(exactly = 1) {
            pollServiceMock.removeVotes(receiverMock, OTHER_3.value)
        }

        monitorJob.cancel()
    }

    companion object {
        private val groupIdentity = mockk<GroupIdentity>()
    }
}
