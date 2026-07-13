package ch.threema.app.voip

import ch.threema.app.eventbus.events.VoipCallEvent
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.services.ContactService
import ch.threema.app.services.MessageService
import ch.threema.app.test.koinTestModuleRule
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData.RejectReason.BUSY
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData.RejectReason.REJECTED
import ch.threema.storage.models.data.status.VoipStatusDataModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testdata.TestData.Identities.ME
import testdata.TestData.Identities.OTHER_1

@OptIn(ExperimentalCoroutinesApi::class)
class VoipCallStatusMonitorTest {

    private lateinit var contactServiceMock: ContactService
    private lateinit var messageServiceMock: MessageService
    private lateinit var voipCallEvents: MutableSharedFlow<VoipCallEvent>
    private lateinit var receiverMock: ContactMessageReceiver
    private lateinit var voipCallStatusMonitor: VoipCallStatusMonitor

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory<ContactService> { contactServiceMock }
        factory<MessageService> { messageServiceMock }
    }

    @BeforeTest
    fun setUp() {
        receiverMock = mockk()
        contactServiceMock = mockk {
            every { createReceiver(OTHER_1.value) } returns receiverMock
        }
        messageServiceMock = mockk(relaxed = true)
        voipCallEvents = MutableSharedFlow()
        voipCallStatusMonitor = VoipCallStatusMonitor(
            globalEventFlows = mockk {
                every { voipCalls } returns voipCallEvents
            },
            identityProvider = mockk {
                every { getIdentity() } returns ME
            },
        )
    }

    @Test
    fun `finishing an outgoing call`() = testCreationOfStatusMessage {
        voipCallEvents.emit(VoipCallEvent.Finished(CALL_ID, peerIdentity = OTHER_1, outgoing = true, duration = 8.seconds))
        verifyVoipStatusCreated(
            status = VoipStatusDataModel.createFinished(CALL_ID, 8.seconds),
            isOutbox = true,
            isRead = true,
        )
    }

    @Test
    fun `finishing an incoming call`() = testCreationOfStatusMessage {
        voipCallEvents.emit(VoipCallEvent.Finished(CALL_ID, peerIdentity = OTHER_1, outgoing = false, duration = 2.minutes))
        verifyVoipStatusCreated(
            status = VoipStatusDataModel.createFinished(CALL_ID, 120.seconds),
            isOutbox = false,
            isRead = true,
        )
    }

    @Test
    fun `an outgoing call is rejected`() = testCreationOfStatusMessage {
        voipCallEvents.emit(VoipCallEvent.Rejected(CALL_ID, peerIdentity = OTHER_1, outgoing = true, reason = BUSY))
        verifyVoipStatusCreated(
            status = VoipStatusDataModel.createRejected(CALL_ID, BUSY),
            isOutbox = true,
            isRead = true,
        )
    }

    @Test
    fun `an incoming call is rejected`() = testCreationOfStatusMessage {
        voipCallEvents.emit(VoipCallEvent.Rejected(CALL_ID, peerIdentity = OTHER_1, outgoing = false, reason = REJECTED))
        verifyVoipStatusCreated(
            status = VoipStatusDataModel.createRejected(CALL_ID, REJECTED),
            isOutbox = false,
            isRead = true,
        )
    }

    @Test
    fun `missing a call, not accepted`() = testCreationOfStatusMessage {
        val createdAt = mockk<Instant>()
        voipCallEvents.emit(VoipCallEvent.Missed(CALL_ID, peerIdentity = OTHER_1, accepted = false, createdAt = createdAt))
        verifyVoipStatusCreated(
            status = VoipStatusDataModel.createMissed(CALL_ID, createdAt),
            isOutbox = false,
            isRead = false,
        )
    }

    @Test
    fun `missing a call, accepted`() = testCreationOfStatusMessage {
        val createdAt = mockk<Instant>()
        voipCallEvents.emit(VoipCallEvent.Missed(CALL_ID, peerIdentity = OTHER_1, accepted = true, createdAt = createdAt))
        verifyVoipStatusCreated(
            status = VoipStatusDataModel.createMissed(CALL_ID, createdAt),
            isOutbox = false,
            isRead = true,
        )
    }

    @Test
    fun `aborting a call`() = testCreationOfStatusMessage {
        voipCallEvents.emit(VoipCallEvent.Aborted(CALL_ID, peerIdentity = OTHER_1))
        verifyVoipStatusCreated(
            status = VoipStatusDataModel.createAborted(CALL_ID),
            isOutbox = true,
            isRead = true,
        )
    }

    private fun testCreationOfStatusMessage(block: suspend () -> Unit) = runTest {
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            voipCallStatusMonitor.run()
        }
        block()

        monitorJob.cancel()
    }

    private fun verifyVoipStatusCreated(status: VoipStatusDataModel, isOutbox: Boolean, isRead: Boolean) {
        verify(exactly = 1) {
            messageServiceMock.createVoipStatus(status, receiverMock, isOutbox, isRead)
        }
    }

    companion object {
        private const val CALL_ID = 123L
    }
}
