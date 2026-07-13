package ch.threema.app.widget

import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.services.LockAppService
import ch.threema.localcrypto.MasterKey
import ch.threema.storage.models.AbstractMessageModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetUpdaterMonitorTest {

    private lateinit var masterKeyFlow: MutableStateFlow<MasterKey?>
    private lateinit var lockStateFlow: MutableStateFlow<LockAppService.LockState>
    private lateinit var conversationEvents: MutableSharedFlow<ConversationEvent>
    private lateinit var messageEvents: MutableSharedFlow<MessageEvent>
    private lateinit var widgetUpdaterMock: WidgetUpdater
    private lateinit var widgetUpdaterMonitor: WidgetUpdaterMonitor

    @BeforeTest
    fun setUp() {
        masterKeyFlow = MutableStateFlow(mockk<MasterKey>())
        messageEvents = MutableSharedFlow()
        conversationEvents = MutableSharedFlow()
        widgetUpdaterMock = mockk(relaxed = true)
        lockStateFlow = MutableStateFlow(LockAppService.LockState.UNLOCKED)
        widgetUpdaterMonitor = WidgetUpdaterMonitor(
            masterKeyProvider = mockk {
                every { watchMasterKey() } returns masterKeyFlow
            },
            globalEventFlows = mockk {
                every { conversations } returns this@WidgetUpdaterMonitorTest.conversationEvents
                every { messages } returns this@WidgetUpdaterMonitorTest.messageEvents
            },
            lockAppService = mockk {
                every { watchLockState() } returns lockStateFlow
            },
            widgetUpdater = widgetUpdaterMock,
        )
    }

    @Test
    fun `widgets are not updated when read message arrives`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 0,
            emitEvents = {
                messageEvents.emit(MessageEvent.NewMessage(mockMessage(isOutbox = false, isStatusMessage = false, isRead = true)))
            },
        )
    }

    @Test
    fun `widgets are not updated when a status message arrives`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 0,
            emitEvents = {
                messageEvents.emit(MessageEvent.NewMessage(mockMessage(isOutbox = false, isStatusMessage = true, isRead = false)))
            },
        )
    }

    @Test
    fun `widgets are not updated for own messages`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 0,
            emitEvents = {
                messageEvents.emit(MessageEvent.NewMessage(mockMessage(isOutbox = true, isStatusMessage = false, isRead = false)))
            },
        )
    }

    @Test
    fun `widgets are updated when new unread message arrives`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 1,
            emitEvents = {
                messageEvents.emit(MessageEvent.NewMessage(mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)))
            },
        )
    }

    @Test
    fun `widgets are updated when a message is edited`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 1,
            emitEvents = {
                messageEvents.emit(MessageEvent.MessageEdited(mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)))
            },
        )
    }

    @Test
    fun `widgets are updated when a message is deleted`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 1,
            emitEvents = {
                messageEvents.emit(MessageEvent.MessageDeletedForAll(mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)))
            },
        )
    }

    @Test
    fun `widgets are updated multiple times when multiple messages come in, but with debouncing`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 3,
            emitEvents = {
                messageEvents.emit(MessageEvent.NewMessage(mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)))
                delay(500.milliseconds)
                messageEvents.emit(MessageEvent.NewMessage(mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)))
                delay(10.seconds)
                messageEvents.emit(MessageEvent.NewMessage(mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)))
                delay(10.seconds)
                messageEvents.emit(MessageEvent.MessageEdited(mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)))
            },
        )
    }

    @Test
    fun `widgets are updated when a conversation event is emitted`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 1,
            emitEvents = {
                conversationEvents.emit(ConversationEvent.ConversationArchived(mockk()))
            },
        )
    }

    @Test
    fun `widgets are updated multiple times when message and conversation events come in, but with debouncing`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 3,
            emitEvents = {
                messageEvents.emit(MessageEvent.NewMessage(mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)))
                delay(500.milliseconds)
                conversationEvents.emit(ConversationEvent.ConversationArchived(mockk()))
                delay(10.seconds)
                conversationEvents.emit(ConversationEvent.ConversationDeleted(mockk()))
                delay(10.seconds)
                messageEvents.emit(MessageEvent.MessageEdited(mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)))
            },
        )
    }

    @Test
    fun `widgets are updated when master key is locked or unlocked`() {
        testWidgetUpdaterMonitor(
            expectedUpdateCount = 3,
            emitEvents = {
                masterKeyFlow.emit(null)
                delay(10.seconds)

                // These 3 events will be ignored, because the master key is locked
                conversationEvents.emit(ConversationEvent.ConversationArchived(mockk()))
                delay(10.seconds)
                conversationEvents.emit(ConversationEvent.ConversationDeleted(mockk()))
                delay(10.seconds)
                conversationEvents.emit(ConversationEvent.ConversationArchived(mockk()))
                delay(10.seconds)

                // Once the master key is unlocked again, events will be collected again
                masterKeyFlow.emit(mockk())
                delay(10.seconds)

                conversationEvents.emit(ConversationEvent.ConversationArchived(mockk()))
                delay(10.seconds)
            },
        )
    }

    private fun testWidgetUpdaterMonitor(
        expectedUpdateCount: Int,
        emitEvents: suspend () -> Unit,
    ) = runTest {
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            widgetUpdaterMonitor.run()
        }

        // The monitor always triggers initially when started, after the 1 second debounce.
        // We delay here so that we can ignore this initial event.
        delay(2.seconds)

        emitEvents()
        advanceUntilIdle()

        // The "+ 1" is to ignore the initial event
        verify(exactly = expectedUpdateCount + 1) { widgetUpdaterMock.updateWidgets() }

        monitorJob.cancel()
    }

    companion object {
        private fun mockMessage(isOutbox: Boolean, isStatusMessage: Boolean, isRead: Boolean): AbstractMessageModel =
            mockk {
                every { this@mockk.isOutbox } returns isOutbox
                every { this@mockk.isStatusMessage } returns isStatusMessage
                every { this@mockk.isRead } returns isRead
            }
    }
}
