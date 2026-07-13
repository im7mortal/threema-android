package ch.threema.app.notifications

import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.services.ConversationService
import ch.threema.app.services.GroupService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.notification.ConversationNotification
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.test.koinTestModuleRule
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.LocalGroupId
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType
import ch.threema.storage.models.data.media.FileDataModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testdata.TestData.Identities.ME
import testdata.TestData.Identities.OTHER_1

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationNotificationUpdaterMonitorTest {

    private lateinit var conversationNotificationConverterMock: ConversationNotificationConverter
    private lateinit var identityProviderMock: IdentityProvider
    private lateinit var notificationServiceMock: NotificationService
    private lateinit var conversationServiceMock: ConversationService
    private lateinit var groupServiceMock: GroupService
    private lateinit var conversationEvents: MutableSharedFlow<ConversationEvent>
    private lateinit var groupEvents: MutableSharedFlow<GroupEvent>
    private lateinit var messageEvents: MutableSharedFlow<MessageEvent>
    private lateinit var lockStateFlow: MutableStateFlow<LockAppService.LockState>
    private lateinit var conversationNotificationUpdaterMonitor: ConversationNotificationUpdaterMonitor

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory<NotificationService> { notificationServiceMock }
        factory<ConversationService> { conversationServiceMock }
        factory<GroupService> { groupServiceMock }
    }

    @BeforeTest
    fun setUp() {
        conversationNotificationConverterMock = mockk()
        identityProviderMock = mockk {
            every { getIdentity() } returns ME
        }
        notificationServiceMock = mockk(relaxed = true)
        conversationServiceMock = mockk(relaxed = true)
        groupServiceMock = mockk(relaxed = true)
        conversationEvents = MutableSharedFlow()
        groupEvents = MutableSharedFlow()
        messageEvents = MutableSharedFlow()
        lockStateFlow = MutableStateFlow(LockAppService.LockState.UNLOCKED)
        conversationNotificationUpdaterMonitor = ConversationNotificationUpdaterMonitor(
            globalEventFlows = mockk {
                every { conversations } returns conversationEvents
                every { groups } returns groupEvents
                every { messages } returns messageEvents
            },
            conversationNotificationConverter = conversationNotificationConverterMock,
            identityProvider = identityProviderMock,
            lockAppService = mockk {
                every { watchLockState() } returns lockStateFlow
            },
        )
    }

    @Test
    fun `conversation notifications are cancelled when conversation is archived`() = runTest {
        val conversationMock = mockk<ConversationModel> {
            every { id } returns ContactConversationId(identity = OTHER_1.value)
        }
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        conversationEvents.emit(ConversationEvent.ConversationArchived(conversationMock))
        advanceUntilIdle()

        verify(exactly = 1) {
            notificationServiceMock.cancel(
                ContactConversationId(identity = OTHER_1.value),
            )
        }

        monitorJob.cancel()
    }

    @Test
    fun `conversation notifications are cancelled when conversation is deleted`() = runTest {
        val conversationMock = mockk<ConversationModel> {
            every { id } returns ContactConversationId(identity = OTHER_1.value)
        }
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        conversationEvents.emit(ConversationEvent.ConversationDeleted(conversationMock))
        advanceUntilIdle()

        verify(exactly = 1) {
            notificationServiceMock.cancel(
                ContactConversationId(identity = OTHER_1.value),
            )
        }

        monitorJob.cancel()
    }

    @Test
    fun `conversation notification is shown upon new message`() = runTest {
        val messageMock = mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)
        val notificationMock = mockk<ConversationNotification>()
        every { conversationNotificationConverterMock.convert(messageMock) } returns notificationMock

        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        messageEvents.emit(MessageEvent.NewMessage(messageMock))
        advanceUntilIdle()

        verify(exactly = 1) {
            notificationServiceMock.showConversationNotification(notificationMock, false)
        }

        monitorJob.cancel()
    }

    @Test
    fun `no conversation notification is shown upon new message when conversation does not exist`() = runTest {
        val messageMock = mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)
        val notificationMock = mockk<ConversationNotification>()
        every { conversationNotificationConverterMock.convert(messageMock) } returns notificationMock
        every { conversationServiceMock.get(messageMock) } returns null

        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        messageEvents.emit(MessageEvent.NewMessage(messageMock))
        advanceUntilIdle()

        verify(exactly = 0) {
            notificationServiceMock.showConversationNotification(any(), any())
        }

        monitorJob.cancel()
    }

    @Test
    fun `no conversation notification is shown for outgoing messages`() = runTest {
        val messageMock = mockMessage(isOutbox = true, isStatusMessage = false, isRead = false)

        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        messageEvents.emit(MessageEvent.NewMessage(messageMock))
        advanceUntilIdle()

        verify(exactly = 0) {
            notificationServiceMock.showConversationNotification(any(), any())
        }

        monitorJob.cancel()
    }

    @Test
    fun `no conversation notification is shown for status messages`() = runTest {
        val messageMock = mockMessage(isOutbox = false, isStatusMessage = true, isRead = false)

        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        messageEvents.emit(MessageEvent.NewMessage(messageMock))
        advanceUntilIdle()

        verify(exactly = 0) {
            notificationServiceMock.showConversationNotification(any(), any())
        }

        monitorJob.cancel()
    }

    @Test
    fun `no conversation notification is shown for read messages`() = runTest {
        val messageMock = mockMessage(isOutbox = false, isStatusMessage = false, isRead = true)

        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        messageEvents.emit(MessageEvent.NewMessage(messageMock))
        advanceUntilIdle()

        verify(exactly = 0) {
            notificationServiceMock.showConversationNotification(any(), any())
        }

        monitorJob.cancel()
    }

    @Test
    fun `no conversation notification is shown for call status messages`() = runTest {
        val messageMock = mockMessage(isOutbox = false, isStatusMessage = false, isRead = false, type = MessageType.GROUP_CALL_STATUS)

        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        messageEvents.emit(MessageEvent.NewMessage(messageMock))
        advanceUntilIdle()

        verify(exactly = 0) {
            notificationServiceMock.showConversationNotification(any(), any())
        }

        monitorJob.cancel()
    }

    @Test
    fun `conversation notification is updated when message is edited`() = runTest {
        val messageMock = mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)
        val notificationMock = mockk<ConversationNotification>()
        every { conversationNotificationConverterMock.convert(messageMock) } returns notificationMock

        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        messageEvents.emit(MessageEvent.MessageEdited(messageMock))
        advanceUntilIdle()

        verify(exactly = 1) {
            notificationServiceMock.showConversationNotification(notificationMock, true)
        }

        monitorJob.cancel()
    }

    @Test
    fun `conversation notification is updated when message is deleted`() = runTest {
        val messageMock = mockMessage(isOutbox = false, isStatusMessage = false, isRead = false)
        val notificationMock = mockk<ConversationNotification>()
        every { conversationNotificationConverterMock.convert(messageMock) } returns notificationMock

        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        messageEvents.emit(MessageEvent.MessageDeletedForAll(messageMock))
        advanceUntilIdle()

        verify(exactly = 1) {
            notificationServiceMock.showConversationNotification(notificationMock, true)
        }

        monitorJob.cancel()
    }

    @Test
    fun `group notifications are cancelled when leaving group`() = runTest {
        val groupIdentity = mockk<GroupIdentity>()
        val groupId = 123
        every { groupServiceMock.getByGroupIdentity(groupIdentity) } returns mockk {
            every { id } returns groupId
        }
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        groupEvents.emit(GroupEvent.UserLeftGroup(groupIdentity))

        verify(exactly = 1) {
            notificationServiceMock.cancelGroupCallNotification(LocalGroupId(groupId))
        }

        monitorJob.cancel()
    }

    @Test
    fun `group notifications are cancelled when kicked from group`() = runTest {
        val groupIdentity = mockk<GroupIdentity>()
        val groupId = 123
        every { groupServiceMock.getByGroupIdentity(groupIdentity) } returns mockk {
            every { id } returns groupId
        }
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        groupEvents.emit(GroupEvent.MemberKicked(groupIdentity, identity = ME))

        verify(exactly = 1) {
            notificationServiceMock.cancelGroupCallNotification(LocalGroupId(groupId))
        }

        monitorJob.cancel()
    }

    @Test
    fun `group notifications are not cancelled when other member is kicked from group`() = runTest {
        val groupIdentity = mockk<GroupIdentity>()
        val groupId = 123
        every { groupServiceMock.getByGroupIdentity(groupIdentity) } returns mockk {
            every { id } returns groupId
        }
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        groupEvents.emit(GroupEvent.MemberKicked(groupIdentity, identity = OTHER_1))

        verify(exactly = 0) {
            notificationServiceMock.cancelGroupCallNotification(LocalGroupId(groupId))
        }

        monitorJob.cancel()
    }

    @Test
    fun `notifications get cancelled when app is locked`() = runTest {
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }
        var expectedCallCount = 0

        lockStateFlow.value = LockAppService.LockState.LOCKED
        expectedCallCount++
        verify(exactly = expectedCallCount) {
            notificationServiceMock.cancelConversationNotificationsOnLockApp()
        }

        delay(1.seconds)
        lockStateFlow.value = LockAppService.LockState.UNLOCKED
        verify(exactly = expectedCallCount) {
            notificationServiceMock.cancelConversationNotificationsOnLockApp()
        }

        delay(1.seconds)
        lockStateFlow.value = LockAppService.LockState.LOCKED
        expectedCallCount++
        verify(exactly = expectedCallCount) {
            notificationServiceMock.cancelConversationNotificationsOnLockApp()
        }

        monitorJob.cancel()
    }

    @Test
    fun `conversation notifications are updated when messages with images or videos are modified`() = runTest {
        val imageMessageMock = mockMessage(
            type = MessageType.FILE,
            messageContentsType = MessageContentsType.IMAGE,
            fileData = mockk {
                every { renderingType } returns FileData.RENDERING_MEDIA
            },
        )
        val videoMessageMock = mockMessage(
            type = MessageType.FILE,
            messageContentsType = MessageContentsType.VIDEO,
            fileData = mockk {
                every { renderingType } returns FileData.RENDERING_MEDIA
            },
        )
        val notificationMock1 = mockk<ConversationNotification>()
        val notificationMock2 = mockk<ConversationNotification>()
        every { conversationNotificationConverterMock.convert(imageMessageMock) } returns notificationMock1
        every { conversationNotificationConverterMock.convert(videoMessageMock) } returns notificationMock2

        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            conversationNotificationUpdaterMonitor.run()
        }

        messageEvents.emit(
            MessageEvent.MessagesUpdated(
                listOf(
                    // Text messages don't update the notification
                    mockMessage(type = MessageType.TEXT),
                    // File messages with default rendering type are irrelevant
                    mockMessage(
                        type = MessageType.FILE,
                        fileData = mockk {
                            every { renderingType } returns FileData.RENDERING_DEFAULT
                        },
                    ),
                    // File messages with non-image/non-video don't update the notification
                    mockMessage(
                        type = MessageType.FILE,
                        messageContentsType = MessageContentsType.AUDIO,
                        fileData = mockk {
                            every { renderingType } returns FileData.RENDERING_MEDIA
                        },
                    ),
                    // File messages with image or video update the notification
                    imageMessageMock,
                    videoMessageMock,
                ),
            ),
        )
        advanceUntilIdle()

        verify(exactly = 1) {
            notificationServiceMock.showConversationNotification(notificationMock1, true)
        }
        verify(exactly = 1) {
            notificationServiceMock.showConversationNotification(notificationMock2, true)
        }

        monitorJob.cancel()
    }

    companion object {
        private fun mockMessage(
            isOutbox: Boolean = false,
            isStatusMessage: Boolean = false,
            isRead: Boolean = false,
            type: MessageType = MessageType.TEXT,
            messageContentsType: Int = 0,
            fileData: FileDataModel = mockk(),
        ): AbstractMessageModel =
            mockk {
                every { this@mockk.isOutbox } returns isOutbox
                every { this@mockk.isStatusMessage } returns isStatusMessage
                every { this@mockk.isRead } returns isRead
                every { this@mockk.type } returns type
                every { this@mockk.messageContentsType } returns messageContentsType
                every { this@mockk.fileData } returns fileData
            }
    }
}
