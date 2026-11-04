package ch.threema.app.protocolsteps

import ch.threema.app.eventbus.EventBus
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.services.MessageService
import ch.threema.data.datatypes.ContactMessageIdentifier
import ch.threema.data.datatypes.DistributionListMessageIdentifier
import ch.threema.data.datatypes.EditHistoryType
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupMessageIdentifier
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.EditHistoryRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.MessageId
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.FileDataModel
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.TestTimeProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData

class CommonEditMessageReceiveStepsTest {

    private lateinit var messageServiceMock: MessageService
    private lateinit var groupModelRepositoryMock: GroupModelRepository
    private lateinit var editHistoryRepositoryMock: EditHistoryRepository
    private val identityProvider = TestIdentityProvider(TestData.Identities.ME)
    private lateinit var globalEventBusesMock: GlobalEventBuses
    private lateinit var messagesEventBusMock: EventBus<MessageEvent>
    private val timeProvider = TestTimeProvider(initialTimestamp = 1042)

    private lateinit var steps: CommonEditMessageReceiveSteps

    private val identity = TestData.Identities.OTHER_1.value
    private val groupCreatorIdentity = TestData.Identities.OTHER_3.value
    private val myIdentity = TestData.Identities.ME.value

    private val editText = "edited text"
    private val editCreatedAt: Instant = Instant.ofEpochMilli(2000)

    @BeforeTest
    fun setup() {
        messageServiceMock = mockk()
        groupModelRepositoryMock = mockk()
        editHistoryRepositoryMock = mockk()
        messagesEventBusMock = mockk()
        globalEventBusesMock = mockk {
            every { messages } returns messagesEventBusMock
        }

        steps = CommonEditMessageReceiveSteps(
            messageService = messageServiceMock,
            groupModelRepository = groupModelRepositoryMock,
            editHistoryRepository = editHistoryRepositoryMock,
            identityProvider = identityProvider,
            globalEventBuses = globalEventBusesMock,
            timeProvider = timeProvider,
        )
    }

    @Test
    fun `distribution list identifier is always discarded`() {
        val identifier = DistributionListMessageIdentifier(distributionListId = 1L, messageId = MessageId(42))

        val result = steps.run(
            editMessageSenderIdentity = identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.DISCARD,
            actual = result,
        )
        verify(exactly = 0) { messageServiceMock.save(any()) }
    }

    @Test
    fun `group identifier with unknown group database id is discarded`() {
        val identifier = GroupMessageIdentifier(groupDatabaseId = 7L, messageId = MessageId(42))
        every { groupModelRepositoryMock.getByGroupDatabaseId(7L) } returns null

        val result = steps.run(
            editMessageSenderIdentity = identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.DISCARD,
            actual = result,
        )
        verify(exactly = 0) { messageServiceMock.getGroupMessageModel(any(), any(), any()) }
    }

    @Test
    fun `group identifier resolves group and looks up group message model`() {
        val messageId = MessageId(42)
        val identifier = GroupMessageIdentifier(groupDatabaseId = 7L, messageId = messageId)
        val groupIdentity = GroupIdentity(creatorIdentity = groupCreatorIdentity, groupId = 123L)
        val groupModel = mockk<GroupModel>()
        every { groupModel.groupIdentity } returns groupIdentity
        every { groupModelRepositoryMock.getByGroupDatabaseId(7L) } returns groupModel

        every {
            messageServiceMock.getGroupMessageModel(messageId, groupCreatorIdentity, any())
        } returns null

        val result = steps.run(
            editMessageSenderIdentity = identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.SUCCESS,
            actual = result,
        )
        verify(exactly = 1) { messageServiceMock.getGroupMessageModel(messageId, groupCreatorIdentity, any()) }
    }

    @Test
    fun `contact identifier looks up contact message model with correct args`() {
        val identifier = ContactMessageIdentifier(messageId = MessageId(5), identity = identity)
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns null

        val result = steps.run(
            editMessageSenderIdentity = identifier.identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.SUCCESS,
            actual = result,
        )
        verify(exactly = 1) { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) }
    }

    @Test
    fun `referred message not found returns SUCCESS without side effects`() {
        val identifier = ContactMessageIdentifier(messageId = MessageId(5), identity = identity)
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns null

        val result = steps.run(
            editMessageSenderIdentity = identifier.identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.SUCCESS,
            actual = result,
        )
        verify(exactly = 0) { editHistoryRepositoryMock.createEntry(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { messageServiceMock.save(any()) }
        verify(exactly = 0) { messagesEventBusMock.emit(any()) }
    }

    @Test
    fun `outbox message uses local identity provider as the sender`() {
        val referredMessage = ContactMessageModel().apply {
            isOutbox = true
            identity = groupCreatorIdentity // irrelevant when isOutbox is true
            type = MessageType.TEXT
            uid = "uid-1"
            body = "original"
        }
        val identifier = ContactMessageIdentifier(
            messageId = MessageId(3),
            identity = identity,
        )
        every { messageServiceMock.save(referredMessage) } just Runs
        every { messagesEventBusMock.emit(any()) } just Runs
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns referredMessage
        every {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-1",
                text = "original",
                createdAt = any(),
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = any(),
            )
        } just Runs

        val result = steps.run(
            editMessageSenderIdentity = myIdentity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.SUCCESS,
            actual = result,
        )
        verify(exactly = 1) { messageServiceMock.save(referredMessage) }
    }

    @Test
    fun `non-editable message type is discarded`() {
        val identifier = ContactMessageIdentifier(
            messageId = MessageId(2),
            identity = identity,
        )
        val referredMessage = ContactMessageModel().apply {
            isOutbox = false
            identity = identifier.identity
            type = MessageType.POLL // not editable
        }
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns referredMessage

        val result = steps.run(
            editMessageSenderIdentity = identifier.identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.DISCARD,
            actual = result,
        )
        verify(exactly = 0) { messageServiceMock.save(any()) }
    }

    @Test
    fun `null message type is discarded`() {
        val identifier = ContactMessageIdentifier(
            messageId = MessageId(2),
            identity = identity,
        )
        val referredMessage = ContactMessageModel().apply {
            isOutbox = false
            identity = identifier.identity
            type = null // not editable because unknown
        }
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns referredMessage

        val result = steps.run(
            editMessageSenderIdentity = identifier.identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.DISCARD,
            actual = result,
        )
        verify(exactly = 0) { messageServiceMock.save(any()) }
    }

    @Test
    fun `successful text edit updates body, editedAt, history and emits events`() {
        val originalCreatedAt = Instant.ofEpochMilli(1000L)
        val identifier = ContactMessageIdentifier(
            messageId = MessageId(4),
            identity = identity,
        )
        val referredMessage = ContactMessageModel().apply {
            isOutbox = false
            identity = identifier.identity
            type = MessageType.TEXT
            body = "original text"
            uid = "uid-1"
            id = 99
            createdAt = originalCreatedAt
            editedAt = null
        }
        every { messageServiceMock.save(referredMessage) } just Runs
        every { messagesEventBusMock.emit(any()) } just Runs
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns referredMessage
        every {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-1",
                text = "original text",
                createdAt = originalCreatedAt,
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = 99,
            )
        } just Runs

        val result = steps.run(
            editMessageSenderIdentity = identifier.identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.SUCCESS,
            actual = result,
        )
        assertEquals(
            expected = editText,
            actual = referredMessage.body,
        )
        assertEquals(
            expected = editCreatedAt,
            actual = referredMessage.editedAt,
        )

        // History contains the *previous* text/createdAt
        verify(exactly = 1) {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-1",
                text = "original text",
                createdAt = originalCreatedAt,
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = 99,
            )
        }

        verify(exactly = 1) { messageServiceMock.save(referredMessage) }
        verify(exactly = 1) { messagesEventBusMock.emit(MessageEvent.MessagesUpdated(referredMessage)) }
        verify(exactly = 1) { messagesEventBusMock.emit(MessageEvent.MessageEdited(referredMessage)) }
    }

    @Test
    fun `previous text createdAt uses editedAt if not null`() {
        val previousEditedAt = Instant.ofEpochMilli(1_500L)
        val identifier = ContactMessageIdentifier(
            messageId = MessageId(4),
            identity = identity,
        )
        val referredMessage = ContactMessageModel().apply {
            isOutbox = false
            identity = identifier.identity
            type = MessageType.TEXT
            body = "original text"
            uid = "uid-1"
            id = 99
            createdAt = Instant.ofEpochMilli(1000)
            editedAt = previousEditedAt
        }
        every { messageServiceMock.save(referredMessage) } just Runs
        every { messagesEventBusMock.emit(any()) } just Runs
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns referredMessage
        every {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-1",
                text = "original text",
                createdAt = previousEditedAt,
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = any(),
            )
        } just Runs

        steps.run(
            editMessageSenderIdentity = identifier.identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        // Should use the previous editedAt, not createdAt or timeProvider.
        verify(exactly = 1) {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-1",
                text = "original text",
                createdAt = previousEditedAt,
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = any(),
            )
        }
    }

    @Test
    fun `previous text createdAt falls back to timeProvider when both createdAt and editedAt are null`() {
        val currentTime = timeProvider.get()
        val identifier = ContactMessageIdentifier(
            messageId = MessageId(4),
            identity = identity,
        )
        val referredMessage = ContactMessageModel().apply {
            isOutbox = false
            identity = identifier.identity
            type = MessageType.TEXT
            body = "original text"
            uid = "uid-3"
            createdAt = null
            editedAt = null
        }
        every { messageServiceMock.save(referredMessage) } just Runs
        every { messagesEventBusMock.emit(any()) } just Runs
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns referredMessage
        every {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-3",
                text = "original text",
                createdAt = currentTime,
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = any(),
            )
        } just Runs

        steps.run(
            editMessageSenderIdentity = identifier.identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        verify(exactly = 1) {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-3",
                text = "original text",
                createdAt = currentTime,
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = any(),
            )
        }
    }

    @Test
    fun `successful file caption edit updates caption, fileData and body`() {
        val fileDataModel = mockk<FileDataModel> {
            var captionValue = "original caption"
            every { caption } answers {
                captionValue
            }
            every { caption = editText } answers {
                captionValue = editText
            }
        }
        val identifier = ContactMessageIdentifier(
            messageId = MessageId(4),
            identity = identity,
        )
        val referredMessage = ContactMessageModel().apply {
            isOutbox = false
            identity = identifier.identity
            type = MessageType.FILE
            uid = "uid-5"
            fileData = fileDataModel
        }
        every { messageServiceMock.save(referredMessage) } just Runs
        every { messagesEventBusMock.emit(any()) } just Runs
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns referredMessage
        every {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-5",
                text = "original caption",
                createdAt = any(),
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = any(),
            )
        } just Runs

        val result = steps.run(
            editMessageSenderIdentity = identifier.identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        assertEquals(
            expected = ReceiveStepsResult.SUCCESS,
            actual = result,
        )
        assertEquals(
            expected = editText,
            actual = referredMessage.caption,
        )
        verify(exactly = 1) { fileDataModel.caption = editText }

        verify(exactly = 1) {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-5",
                text = "original caption",
                createdAt = any(),
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = any(),
            )
        }
        verify(exactly = 1) { messageServiceMock.save(referredMessage) }
    }

    @Test
    fun `file message with null caption records empty string as previous text`() {
        val fileDataModel = mockk<FileDataModel> {
            every { caption } returns null
            every { caption = "edited text" } just Runs
        }
        val identifier = ContactMessageIdentifier(
            messageId = MessageId(4),
            identity = identity,
        )
        val referredMessage = ContactMessageModel().apply {
            isOutbox = false
            identity = identifier.identity
            type = MessageType.FILE
            uid = "uid-5"
            fileData = fileDataModel
        }
        every { messageServiceMock.save(referredMessage) } just Runs
        every { messagesEventBusMock.emit(any()) } just Runs
        every { messageServiceMock.getContactMessageModel(identifier.messageId, identifier.identity) } returns referredMessage
        every {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-5",
                text = "",
                createdAt = any(),
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = any(),
            )
        } just Runs

        steps.run(
            editMessageSenderIdentity = identifier.identity,
            editMessageText = editText,
            editMessageCreatedAt = editCreatedAt,
            referredMessageIdentifier = identifier,
        )

        verify(exactly = 1) {
            editHistoryRepositoryMock.createEntry(
                messageUid = "uid-5",
                text = "",
                createdAt = any(),
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = any(),
            )
        }
    }
}
