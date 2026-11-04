package ch.threema.app.libthreema.conversion

import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.libthreema.ConversationId
import ch.threema.libthreema.DeletedMessageState
import ch.threema.libthreema.EmojiReaction
import ch.threema.libthreema.IncomingMessageDirection
import ch.threema.libthreema.MessageDirection
import ch.threema.libthreema.MessageOverrides
import ch.threema.libthreema.MessageOverridesDeliveryReceiptPolicy
import ch.threema.libthreema.MessageReaction
import ch.threema.libthreema.MessageStarMarker
import ch.threema.libthreema.MessageState
import ch.threema.libthreema.MessageStateBody
import ch.threema.libthreema.MessageStateBodyType
import ch.threema.libthreema.OutgoingMessageDirection
import ch.threema.libthreema.PollClose
import ch.threema.libthreema.PollOpen
import ch.threema.storage.factories.PollModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.PollDataModel
import ch.threema.test.TestIdentityProvider
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData

class MessageModelConverterTest {

    @Test
    fun `conversion of incoming contact message`() {
        assertCorrectConversion(
            messageContainer = createIncomingContactMessageContainer(),
        )
    }

    @Test
    fun `conversion of outgoing contact message`() {
        assertCorrectConversion(
            messageContainer = createOutgoingContactMessageContainer(),
        )
    }

    @Test
    fun `conversion of incoming group message`() {
        assertCorrectConversion(
            messageContainer = createIncomingGroupMessageContainer(),
        )
    }

    @Test
    fun `conversion of outgoing group message`() {
        assertCorrectConversion(
            messageContainer = createOutgoingGroupMessageContainer(),
        )
    }

    @Test
    fun `conversion of incoming distribution list message`() {
        assertCorrectConversion(
            messageContainer = createIncomingDistributionListMessageContainer(),
        )
    }

    @Test
    fun `conversion of outgoing distribution list message`() {
        assertCorrectConversion(
            messageContainer = createOutgoingDistributionListMessageContainer(),
        )
    }

    @Test
    fun `conversation of poll open message`() {
        val messageModelConverter = createMessageModelConverter(
            pollModelFactory = mockk {
                every { getApiPollIdByLocalPollId(pollId = 123) } returns "fe"
            },
        )
        val messageModel = ContactMessageModel().setUp {
            identity = TestData.Identities.OTHER_1.value
            pollData = PollDataModel.createCreated(pollId = 123)
        }
        val messageState = messageModelConverter.convertToLibthreemaMessageState(
            messageModel = messageModel,
            emojiReactions = null,
            editHistoryListModel = null,
        )
        assertEquals(
            MessageStateBody.PollOpen(PollOpen(0xfeUL)),
            messageState.body,
        )
    }

    @Test
    fun `conversation of poll close message`() {
        val messageModelConverter = createMessageModelConverter(
            pollModelFactory = mockk {
                every { getApiPollIdByLocalPollId(pollId = 123) } returns "fe"
            },
        )
        val messageModel = ContactMessageModel().setUp {
            identity = TestData.Identities.OTHER_1.value
            pollData = PollDataModel.createClosed(pollId = 123)
        }
        val messageState = messageModelConverter.convertToLibthreemaMessageState(
            messageModel = messageModel,
            emojiReactions = null,
            editHistoryListModel = null,
        )
        assertEquals(
            MessageStateBody.PollClose(PollClose(0xfeUL)),
            messageState.body,
        )
    }

    @Test
    fun `conversation of deleted text message`() {
        val messageModelConverter = createMessageModelConverter()
        val messageModel = ContactMessageModel().setUp {
            type = MessageType.TEXT
            identity = TestData.Identities.OTHER_1.value
            deletedAt = Instant.ofEpochMilli(1234)
        }
        val messageState = messageModelConverter.convertToLibthreemaMessageState(
            messageModel = messageModel,
            emojiReactions = null,
            editHistoryListModel = null,
        )
        assertEquals(
            MessageStateBody.Deleted(
                v1 = DeletedMessageState(
                    previousMessageType = MessageStateBodyType.TEXT,
                    deletedAt = 1234UL,
                ),
            ),
            messageState.body,
        )
    }

    @Test
    fun `conversation of deleted location message`() {
        val messageModelConverter = createMessageModelConverter()
        val messageModel = ContactMessageModel().setUp {
            type = MessageType.LOCATION
            identity = TestData.Identities.OTHER_1.value
            deletedAt = Instant.ofEpochMilli(1234)
        }
        val messageState = messageModelConverter.convertToLibthreemaMessageState(
            messageModel = messageModel,
            emojiReactions = null,
            editHistoryListModel = null,
        )
        assertEquals(
            MessageStateBody.Deleted(
                v1 = DeletedMessageState(
                    previousMessageType = MessageStateBodyType.LOCATION,
                    deletedAt = 1234UL,
                ),
            ),
            messageState.body,
        )
    }

    private fun assertCorrectConversion(messageContainer: MessageContainer) {
        // Arrange
        val messageModelConverter = createMessageModelConverter(
            groupModelRepository = messageContainer.groupModelRepositoryMock,
            textMessageBodyConverter = mockk {
                every {
                    convertToLibthreemaTextMessageState(
                        messageModel = messageContainer.messageModel,
                        editHistoryEntries = null,
                    )
                } returns
                    (messageContainer.messageState.body as MessageStateBody.Text).v1
            },
        )

        // Act
        val actualMessageState = messageModelConverter.convertToLibthreemaMessageState(
            messageModel = messageContainer.messageModel,
            emojiReactions = EmojiReactionsModel(
                data = messageContainer.emojiReactions,
                multiDeviceManager = mockk(),
                taskManager = mockk(),
            ),
            editHistoryListModel = null,
        )

        // Assert
        assertEquals(
            expected = messageContainer.messageState,
            actual = actualMessageState,
        )
    }

    private fun createIncomingContactMessageContainer(): MessageContainer {
        val senderIdentity = TestData.Identities.OTHER_1.value
        // Note: This identity is the same as the sender as we are in a 1:1 conversation
        val reactorIdentity = TestData.Identities.OTHER_1.value
        val messageModel = ContactMessageModel().setUp {
            identity = senderIdentity
            isOutbox = false
            addMessageFlag(ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS)
        }

        val emojiReactions = listOf(
            EmojiReactionData(
                messageId = 0,
                senderIdentity = reactorIdentity,
                emojiSequence = "\uD83D\uDE00\uFE0F",
                reactedAt = Instant.ofEpochMilli(120),
            ),
        )

        val messageState = MessageState(
            conversationId = ConversationId.Contact(senderIdentity),
            creatorIdentity = senderIdentity,
            messageId = 42u,
            createdAt = 42u,
            direction = MessageDirection.Incoming(
                v1 = IncomingMessageDirection(
                    receivedAt = 44u,
                    overrides = MessageOverrides(
                        deliveryReceiptPolicy = MessageOverridesDeliveryReceiptPolicy.DONT_SEND,
                    ),
                ),
            ),
            readAt = 45u,
            starMarker = MessageStarMarker.NONE,
            reactions = listOf(
                MessageReaction(
                    createdAt = 120u,
                    creatorIdentity = reactorIdentity,
                    emoji = EmojiReaction(
                        codepoint = "\uD83D\uDE00\uFE0F",
                    ),
                ),
            ),
            body = MessageStateBody.Text(
                v1 = mockk(),
            ),
        )

        return MessageContainer(
            messageState = messageState,
            messageModel = messageModel,
            emojiReactions = emojiReactions,
        )
    }

    private fun createOutgoingContactMessageContainer(): MessageContainer {
        val myIdentity = TestData.Identities.ME.value
        val receiverIdentity = TestData.Identities.OTHER_1.value
        // Note: This identity is the same as the receiver as we are in a 1:1 conversation
        val reactorIdentity = TestData.Identities.OTHER_1.value
        val messageModel = ContactMessageModel().setUp {
            identity = receiverIdentity
            isOutbox = true
            postedAt = Instant.ofEpochMilli(43)
        }

        val emojiReactions = listOf(
            EmojiReactionData(
                messageId = 0,
                senderIdentity = reactorIdentity,
                emojiSequence = "\uD83D\uDE00\uFE0F",
                reactedAt = Instant.ofEpochMilli(120),
            ),
        )

        val messageState = MessageState(
            conversationId = ConversationId.Contact(receiverIdentity),
            creatorIdentity = myIdentity,
            messageId = 42u,
            createdAt = 42u,
            direction = MessageDirection.Outgoing(
                v1 = OutgoingMessageDirection(
                    sentAt = 43u,
                    deliveredAt = 44u,
                ),
            ),
            readAt = 45u,
            starMarker = MessageStarMarker.NONE,
            reactions = listOf(
                MessageReaction(
                    createdAt = 120u,
                    creatorIdentity = reactorIdentity,
                    emoji = EmojiReaction(
                        codepoint = "\uD83D\uDE00\uFE0F",
                    ),
                ),
            ),
            body = MessageStateBody.Text(
                v1 = mockk(),
            ),
        )

        return MessageContainer(
            messageState = messageState,
            messageModel = messageModel,
            emojiReactions = emojiReactions,
        )
    }

    private fun createIncomingGroupMessageContainer(): MessageContainer {
        val groupDatabaseId = 1
        val groupIdentity = GroupIdentity(
            creatorIdentity = TestData.Identities.OTHER_3.value,
            groupId = 42,
        )
        val senderIdentity = TestData.Identities.OTHER_1.value
        val reactorIdentity = TestData.Identities.OTHER_2.value
        val messageModel = GroupMessageModel().setUp {
            groupId = groupDatabaseId
            identity = senderIdentity
            isOutbox = false
        }

        val emojiReactions = listOf(
            EmojiReactionData(
                messageId = 0,
                senderIdentity = reactorIdentity,
                emojiSequence = "\uD83D\uDE00\uFE0F",
                reactedAt = Instant.ofEpochMilli(120),
            ),
        )

        val messageState = MessageState(
            conversationId = ConversationId.Group(groupIdentity.toLibthreemaGroupIdentity()),
            creatorIdentity = senderIdentity,
            messageId = 42u,
            createdAt = 42u,
            direction = MessageDirection.Incoming(
                v1 = IncomingMessageDirection(
                    receivedAt = 44u,
                    overrides = MessageOverrides(
                        deliveryReceiptPolicy = MessageOverridesDeliveryReceiptPolicy.DEFAULT,
                    ),
                ),
            ),
            readAt = 45u,
            starMarker = MessageStarMarker.NONE,
            reactions = listOf(
                MessageReaction(
                    createdAt = 120u,
                    creatorIdentity = reactorIdentity,
                    emoji = EmojiReaction(
                        codepoint = "\uD83D\uDE00\uFE0F",
                    ),
                ),
            ),
            body = MessageStateBody.Text(
                v1 = mockk(),
            ),
        )

        val groupModelRepositoryMock: GroupModelRepository = mockk {
            every { getByGroupDatabaseId(groupDatabaseId.toLong()) } returns mockk groupModelMock@{
                every { this@groupModelMock.groupIdentity } returns groupIdentity
            }
        }

        return MessageContainer(
            messageState = messageState,
            messageModel = messageModel,
            emojiReactions = emojiReactions,
            groupModelRepositoryMock = groupModelRepositoryMock,
        )
    }

    private fun createOutgoingGroupMessageContainer(): MessageContainer {
        val groupDatabaseId = 1
        val groupIdentity = GroupIdentity(
            creatorIdentity = TestData.Identities.OTHER_3.value,
            groupId = 42,
        )
        val senderIdentity = TestData.Identities.ME.value
        val reactorIdentity = TestData.Identities.OTHER_2.value
        val messageModel = GroupMessageModel().setUp {
            groupId = groupDatabaseId
            identity = null
            isOutbox = true
            postedAt = Instant.ofEpochMilli(43)
        }

        val emojiReactions = listOf(
            EmojiReactionData(
                messageId = 0,
                senderIdentity = reactorIdentity,
                emojiSequence = "\uD83D\uDE00\uFE0F",
                reactedAt = Instant.ofEpochMilli(120),
            ),
        )

        val messageState = MessageState(
            conversationId = ConversationId.Group(groupIdentity.toLibthreemaGroupIdentity()),
            creatorIdentity = senderIdentity,
            messageId = 42u,
            createdAt = 42u,
            direction = MessageDirection.Outgoing(
                v1 = OutgoingMessageDirection(
                    sentAt = 43u,
                    deliveredAt = 44u,
                ),
            ),
            readAt = 45u,
            starMarker = MessageStarMarker.NONE,
            reactions = listOf(
                MessageReaction(
                    createdAt = 120u,
                    creatorIdentity = reactorIdentity,
                    emoji = EmojiReaction(
                        codepoint = "\uD83D\uDE00\uFE0F",
                    ),
                ),
            ),
            body = MessageStateBody.Text(
                v1 = mockk(),
            ),
        )

        val groupModelRepositoryMock: GroupModelRepository = mockk {
            every { getByGroupDatabaseId(groupDatabaseId.toLong()) } returns mockk groupModelMock@{
                every { this@groupModelMock.groupIdentity } returns groupIdentity
            }
        }

        return MessageContainer(
            messageState = messageState,
            messageModel = messageModel,
            emojiReactions = emojiReactions,
            groupModelRepositoryMock = groupModelRepositoryMock,
        )
    }

    private fun createIncomingDistributionListMessageContainer(): MessageContainer {
        val distributionListDatabaseId = 1L
        val senderIdentity = TestData.Identities.OTHER_1.value
        val reactorIdentity = TestData.Identities.OTHER_2.value
        val messageModel = DistributionListMessageModel().setUp {
            distributionListId = distributionListDatabaseId
            identity = senderIdentity
            isOutbox = false
        }

        val emojiReactions = listOf(
            EmojiReactionData(
                messageId = 0,
                senderIdentity = reactorIdentity,
                emojiSequence = "\uD83D\uDE00\uFE0F",
                reactedAt = Instant.ofEpochMilli(120),
            ),
        )

        val messageState = MessageState(
            conversationId = ConversationId.DistributionList(distributionListDatabaseId.toULong()),
            creatorIdentity = senderIdentity,
            messageId = 42u,
            createdAt = 42u,
            direction = MessageDirection.Incoming(
                v1 = IncomingMessageDirection(
                    receivedAt = 44u,
                    overrides = MessageOverrides(
                        deliveryReceiptPolicy = MessageOverridesDeliveryReceiptPolicy.DEFAULT,
                    ),
                ),
            ),
            readAt = 45u,
            starMarker = MessageStarMarker.NONE,
            reactions = listOf(
                MessageReaction(
                    createdAt = 120u,
                    creatorIdentity = reactorIdentity,
                    emoji = EmojiReaction(
                        codepoint = "\uD83D\uDE00\uFE0F",
                    ),
                ),
            ),
            body = MessageStateBody.Text(
                v1 = mockk(),
            ),
        )

        return MessageContainer(
            messageState = messageState,
            messageModel = messageModel,
            emojiReactions = emojiReactions,
            groupModelRepositoryMock = mockk(),
        )
    }

    private fun createOutgoingDistributionListMessageContainer(): MessageContainer {
        val distributionListDatabaseId = 1L

        val senderIdentity = TestData.Identities.ME.value
        val reactorIdentity = TestData.Identities.OTHER_2.value
        val messageModel = DistributionListMessageModel().setUp {
            distributionListId = distributionListDatabaseId
            identity = null
            isOutbox = true
            postedAt = Instant.ofEpochMilli(43)
        }

        val emojiReactions = listOf(
            EmojiReactionData(
                messageId = 0,
                senderIdentity = reactorIdentity,
                emojiSequence = "\uD83D\uDE00\uFE0F",
                reactedAt = Instant.ofEpochMilli(120),
            ),
        )

        val messageState = MessageState(
            conversationId = ConversationId.DistributionList(distributionListDatabaseId.toULong()),
            creatorIdentity = senderIdentity,
            messageId = 42u,
            createdAt = 42u,
            direction = MessageDirection.Outgoing(
                v1 = OutgoingMessageDirection(
                    sentAt = 43u,
                    deliveredAt = 44u,
                ),
            ),
            readAt = 45u,
            starMarker = MessageStarMarker.NONE,
            reactions = listOf(
                MessageReaction(
                    createdAt = 120u,
                    creatorIdentity = reactorIdentity,
                    emoji = EmojiReaction(
                        codepoint = "\uD83D\uDE00\uFE0F",
                    ),
                ),
            ),
            body = MessageStateBody.Text(
                v1 = mockk(),
            ),
        )

        return MessageContainer(
            messageState = messageState,
            messageModel = messageModel,
            emojiReactions = emojiReactions,
            groupModelRepositoryMock = mockk(),
        )
    }

    private fun <T : MessageModel> T.setUp(apply: T.() -> Unit): T = apply {
        id = 0
        uid = "muid"
        messageId = MessageId(42)
        type = MessageType.TEXT
        correlationId = null
        body = "Message revision 3"
        createdAt = Instant.ofEpochMilli(42)
        deliveredAt = Instant.ofEpochMilli(44)
        readAt = Instant.ofEpochMilli(45)
        editedAt = Instant.ofEpochMilli(126)
        apply()
    }

    private data class MessageContainer(
        val messageState: MessageState,
        val messageModel: MessageModel,
        val emojiReactions: List<EmojiReactionData>,
        val groupModelRepositoryMock: GroupModelRepository = mockk(),
    )

    private fun createMessageModelConverter(
        pollModelFactory: PollModelFactory = mockk(),
        groupModelRepository: GroupModelRepository = mockk(),
        textMessageBodyConverter: TextMessageBodyConverter = mockk(),
    ) =
        MessageModelConverter(
            groupModelRepository = groupModelRepository,
            pollModelFactory = pollModelFactory,
            identityProvider = TestIdentityProvider(TestData.Identities.ME),
            textMessageBodyConverter = textMessageBodyConverter,
            locationMessageBodyConverter = mockk(),
        )
}
