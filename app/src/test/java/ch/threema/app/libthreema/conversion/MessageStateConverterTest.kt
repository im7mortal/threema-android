package ch.threema.app.libthreema.conversion

import ch.threema.common.UUIDGenerator
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.ConversationId as LibthreemaConversationId
import ch.threema.libthreema.GroupIdentity as LibthreemaGroupIdentity
import ch.threema.libthreema.IncomingMessageDirection as LibthreemaIncomingMessageDirection
import ch.threema.libthreema.MessageDirection as LibthreemaMessageDirection
import ch.threema.libthreema.MessageOverrides as LibthreemaMessageOverrides
import ch.threema.libthreema.MessageOverridesDeliveryReceiptPolicy as LibthreemaMessageOverridesDeliveryReceiptPolicy
import ch.threema.libthreema.MessageReaction as LibthreemaMessageReaction
import ch.threema.libthreema.MessageStarMarker as LibthreemaMessageStarMarker
import ch.threema.libthreema.MessageState as LibthreemaMessageState
import ch.threema.libthreema.MessageStateBody as LibthreemaMessageStateBody
import ch.threema.libthreema.OutgoingMessageDirection as LibthreemaOutgoingMessageDirection
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.testhelpers.TestTimeProvider
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import testdata.TestData

class MessageStateConverterTest {

    @Test
    fun `message body state conversion results are applied`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val messageStateBody: LibthreemaMessageStateBody = mockk()
        val contactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            body = messageStateBody,
        )
        val messageBodyAndRevisions = getExampleMessageBodyAndRevisions()
        val messageBodyStateConverterMock: MessageBodyStateConverter = mockk {
            every { convertToMessageBodyAndRevisions(messageStateBody) } returns messageBodyAndRevisions
        }
        val messageStateConverter = getMessageStateConverter(
            messageBodyStateConverter = messageBodyStateConverterMock,
        )

        // act
        val messageStateConversionResult = messageStateConverter.convertContactMessage(contactMessageState)

        // assert
        assertEquals(
            expected = messageBodyAndRevisions.originalBody,
            actual = messageStateConversionResult.messageModel.body,
        )
        assertEquals(
            expected = false,
            actual = messageStateConversionResult.messageModel.isStatusMessage,
        )
        assertEquals(
            expected = messageBodyAndRevisions.messageType,
            actual = messageStateConversionResult.messageModel.type,
        )
    }

    @Test
    fun `uid is generated`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val contactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
        )
        val messageBodyAndRevisions = getExampleMessageBodyAndRevisions()
        val messageBodyStateConverterMock: MessageBodyStateConverter = mockk {
            every { convertToMessageBodyAndRevisions(any()) } returns messageBodyAndRevisions
        }
        val uuidString = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        val messageStateConverter = getMessageStateConverter(
            messageBodyStateConverter = messageBodyStateConverterMock,
            uuidGenerator = {
                UUID.fromString(uuidString)
            },
        )

        // act
        val messageStateConversionResult = messageStateConverter.convertContactMessage(contactMessageState)

        // assert
        assertEquals(
            expected = uuidString,
            actual = messageStateConversionResult.messageModel.uid,
        )
    }

    @Test
    fun `common properties are set correctly`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val contactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val messageStateConversionResult = messageStateConverter.convertContactMessage(contactMessageState)

        // assert
        assertEquals(
            expected = true,
            actual = messageStateConversionResult.messageModel.isSaved,
        )
        assertEquals(
            expected = contactMessageState.messageId.toLong(),
            actual = messageStateConversionResult.messageModel.messageId?.messageIdLong,
        )
        assertEquals(
            expected = contactMessageState.createdAt.toLong(),
            actual = messageStateConversionResult.messageModel.createdAt?.toEpochMilli(),
        )
    }

    @Test
    fun `readAt is set correctly`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val readContactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            readAt = 42u,
        )
        val unreadContactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            readAt = null,
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val readMessageStateConversionResult = messageStateConverter.convertContactMessage(readContactMessageState)
        val unreadMessageStateConversionResult = messageStateConverter.convertContactMessage(unreadContactMessageState)

        // assert
        assertEquals(
            expected = readContactMessageState.readAt?.toLong(),
            actual = readMessageStateConversionResult.messageModel.readAt?.toEpochMilli(),
        )
        assertEquals(
            expected = true,
            actual = readMessageStateConversionResult.messageModel.isRead,
        )

        assertEquals(
            expected = null,
            actual = unreadMessageStateConversionResult.messageModel.readAt?.toEpochMilli(),
        )
        assertEquals(
            expected = false,
            actual = unreadMessageStateConversionResult.messageModel.isRead,
        )
    }

    @Test
    fun `star marker is set correctly`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val normalContactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            starMarker = LibthreemaMessageStarMarker.NONE,
        )
        val starredContactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            starMarker = LibthreemaMessageStarMarker.STAR,
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val normalMessageStateConversionResult = messageStateConverter.convertContactMessage(normalContactMessageState)
        val starredMessageStateConversionResult = messageStateConverter.convertContactMessage(starredContactMessageState)

        // assert
        assertEquals(
            expected = false,
            actual = normalMessageStateConversionResult.messageModel.isStarred,
        )
        assertEquals(
            expected = true,
            actual = starredMessageStateConversionResult.messageModel.isStarred,
        )
    }

    @Test
    fun `state for incoming messages is set correctly`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val createdAt = 0UL
        val receivedAt = 42UL
        val readAt = 84UL
        val readContactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            direction = LibthreemaMessageDirection.Incoming(
                LibthreemaIncomingMessageDirection(
                    receivedAt = receivedAt,
                    overrides = LibthreemaMessageOverrides(
                        deliveryReceiptPolicy = LibthreemaMessageOverridesDeliveryReceiptPolicy.DEFAULT,
                    ),
                ),
            ),
            createdAt = createdAt,
            readAt = readAt,
        )
        val deliveredContactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            direction = LibthreemaMessageDirection.Incoming(
                LibthreemaIncomingMessageDirection(
                    receivedAt = receivedAt,
                    overrides = LibthreemaMessageOverrides(
                        deliveryReceiptPolicy = LibthreemaMessageOverridesDeliveryReceiptPolicy.DONT_SEND,
                    ),
                ),
            ),
            createdAt = createdAt,
            readAt = null,
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val readMessageStateConversionResult = messageStateConverter.convertContactMessage(readContactMessageState)
        val deliveredMessageStateConversionResult = messageStateConverter.convertContactMessage(deliveredContactMessageState)

        // assert
        readMessageStateConversionResult.messageModel.let { readMessageModel ->
            assertEquals(
                expected = MessageState.READ,
                actual = readMessageModel.state,
            )
            assertEquals(
                expected = createdAt,
                actual = readMessageModel.postedAt?.toEpochMilli()?.toULong(),
            )
            assertFalse(readMessageModel.isOutbox)
            assertFalse(readMessageModel.hasMessageFlag(ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS))
        }

        deliveredMessageStateConversionResult.messageModel.let { deliveredMessageModel ->
            assertEquals(
                expected = MessageState.DELIVERED,
                actual = deliveredMessageModel.state,
            )
            assertNull(deliveredMessageModel.rawPostedAt)
            assertFalse(deliveredMessageModel.isOutbox)
            assertTrue(deliveredMessageModel.hasMessageFlag(ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS))
        }
    }

    @Test
    fun `state for read outgoing messages is set correctly`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val createdAt = 0UL
        val sentAt = 42UL
        val deliveredAt = 84UL
        val readAt = 126UL

        val outgoingMessageDirection = LibthreemaMessageDirection.Outgoing(
            LibthreemaOutgoingMessageDirection(
                sentAt = sentAt,
                deliveredAt = deliveredAt,
            ),
        )
        val contactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            direction = outgoingMessageDirection,
            createdAt = createdAt,
            readAt = readAt,
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val messageStateConversionResult = messageStateConverter.convertContactMessage(contactMessageState)

        // assert
        assertEquals(
            expected = MessageState.READ,
            actual = messageStateConversionResult.messageModel.state,
        )
        assertEquals(
            expected = readAt.toLong(),
            actual = messageStateConversionResult.messageModel.readAt?.toEpochMilli(),
        )
        assertEquals(
            expected = deliveredAt.toLong(),
            actual = messageStateConversionResult.messageModel.deliveredAt?.toEpochMilli(),
        )
        assertEquals(
            expected = sentAt.toLong(),
            actual = messageStateConversionResult.messageModel.postedAt?.toEpochMilli(),
        )
        assertTrue(messageStateConversionResult.messageModel.isOutbox)
    }

    @Test
    fun `state for delivered outgoing messages is set correctly`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val createdAt = 0UL
        val sentAt = 42UL
        val deliveredAt = 84UL

        val outgoingMessageDirection = LibthreemaMessageDirection.Outgoing(
            LibthreemaOutgoingMessageDirection(
                sentAt = sentAt,
                deliveredAt = deliveredAt,
            ),
        )
        val contactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            direction = outgoingMessageDirection,
            createdAt = createdAt,
            readAt = null,
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val messageStateConversionResult = messageStateConverter.convertContactMessage(contactMessageState)

        // assert
        assertEquals(
            expected = MessageState.DELIVERED,
            actual = messageStateConversionResult.messageModel.state,
        )
        assertNull(messageStateConversionResult.messageModel.readAt)
        assertEquals(
            expected = deliveredAt.toLong(),
            actual = messageStateConversionResult.messageModel.deliveredAt?.toEpochMilli(),
        )
        assertEquals(
            expected = sentAt.toLong(),
            actual = messageStateConversionResult.messageModel.postedAt?.toEpochMilli(),
        )
        assertTrue(messageStateConversionResult.messageModel.isOutbox)
    }

    @Test
    fun `state for sent outgoing messages is set correctly`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val createdAt = 0UL
        val sentAt = 42UL

        val outgoingMessageDirection = LibthreemaMessageDirection.Outgoing(
            LibthreemaOutgoingMessageDirection(
                sentAt = sentAt,
                deliveredAt = null,
            ),
        )
        val contactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            direction = outgoingMessageDirection,
            createdAt = createdAt,
            readAt = null,
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val messageStateConversionResult = messageStateConverter.convertContactMessage(contactMessageState)

        // assert
        assertEquals(
            expected = MessageState.SENT,
            actual = messageStateConversionResult.messageModel.state,
        )
        assertNull(messageStateConversionResult.messageModel.readAt)
        assertNull(messageStateConversionResult.messageModel.deliveredAt)
        assertEquals(
            expected = sentAt.toLong(),
            actual = messageStateConversionResult.messageModel.postedAt?.toEpochMilli(),
        )
        assertTrue(messageStateConversionResult.messageModel.isOutbox)
    }

    @Test
    fun `state for unsent outgoing messages is set correctly`() {
        // arrange
        val identity = TestData.Identities.OTHER_1.value
        val createdAt = 0UL

        val outgoingMessageDirection = LibthreemaMessageDirection.Outgoing(
            LibthreemaOutgoingMessageDirection(
                sentAt = null,
                deliveredAt = null,
            ),
        )
        val contactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            direction = outgoingMessageDirection,
            createdAt = createdAt,
            readAt = null,
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val messageStateConversionResult = messageStateConverter.convertContactMessage(contactMessageState)

        // assert
        assertEquals(
            expected = MessageState.SENDFAILED,
            actual = messageStateConversionResult.messageModel.state,
        )
        assertNull(messageStateConversionResult.messageModel.readAt)
        assertNull(messageStateConversionResult.messageModel.deliveredAt)
        assertNull(messageStateConversionResult.messageModel.rawPostedAt)
        assertTrue(messageStateConversionResult.messageModel.isOutbox)
    }

    @Test
    fun `identity for contact message is set correctly`() {
        // arrange
        val myIdentity = TestData.Identities.ME.value
        val identity = TestData.Identities.OTHER_1.value

        val incomingContactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = identity,
            direction = LibthreemaMessageDirection.Incoming(
                LibthreemaIncomingMessageDirection(
                    receivedAt = 42u,
                    overrides = LibthreemaMessageOverrides(
                        deliveryReceiptPolicy = LibthreemaMessageOverridesDeliveryReceiptPolicy.DEFAULT,
                    ),
                ),
            ),
        )
        val outgoingContactMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Contact(identity),
            creatorIdentity = myIdentity,
            direction = LibthreemaMessageDirection.Outgoing(
                LibthreemaOutgoingMessageDirection(
                    sentAt = null,
                    deliveredAt = null,
                ),
            ),
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val incomingMessageStateConversionResult = messageStateConverter.convertContactMessage(incomingContactMessageState)
        val outgoingMessageStateConversionResult = messageStateConverter.convertContactMessage(outgoingContactMessageState)

        // assert
        assertEquals(
            expected = identity,
            actual = incomingMessageStateConversionResult.messageModel.identity,
        )
        assertEquals(
            expected = identity,
            actual = outgoingMessageStateConversionResult.messageModel.identity,
        )
    }

    @Test
    fun `identity for group message is set correctly`() {
        // arrange
        val myIdentity = TestData.Identities.ME.value
        val identity = TestData.Identities.OTHER_1.value
        val groupIdentity = LibthreemaGroupIdentity(
            creatorIdentity = identity,
            groupId = 12u,
        )

        val incomingGroupMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Group(groupIdentity),
            creatorIdentity = identity,
            direction = LibthreemaMessageDirection.Incoming(
                LibthreemaIncomingMessageDirection(
                    receivedAt = 42u,
                    overrides = LibthreemaMessageOverrides(
                        deliveryReceiptPolicy = LibthreemaMessageOverridesDeliveryReceiptPolicy.DEFAULT,
                    ),
                ),
            ),
        )
        val outgoingGroupMessageState = getMessageState(
            conversationId = LibthreemaConversationId.Group(groupIdentity),
            creatorIdentity = myIdentity,
            direction = LibthreemaMessageDirection.Outgoing(
                LibthreemaOutgoingMessageDirection(
                    sentAt = null,
                    deliveredAt = null,
                ),
            ),
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val incomingMessageStateConversionResult = messageStateConverter.convertGroupMessage(
            groupMessageState = incomingGroupMessageState,
            groupDatabaseId = 1L,
        )
        val outgoingMessageStateConversionResult = messageStateConverter.convertGroupMessage(
            groupMessageState = outgoingGroupMessageState,
            groupDatabaseId = 2L,
        )

        // assert
        assertEquals(
            expected = identity,
            actual = incomingMessageStateConversionResult.messageModel.identity,
        )
        assertNull(outgoingMessageStateConversionResult.messageModel.identity)
    }

    @Test
    fun `identity for distribution list message is set correctly`() {
        // arrange
        val myIdentity = TestData.Identities.ME.value
        val identity = TestData.Identities.OTHER_1.value

        val incomingDistListMessageState = getMessageState(
            conversationId = LibthreemaConversationId.DistributionList(2u),
            creatorIdentity = identity,
            direction = LibthreemaMessageDirection.Incoming(
                LibthreemaIncomingMessageDirection(
                    receivedAt = 42u,
                    overrides = LibthreemaMessageOverrides(
                        deliveryReceiptPolicy = LibthreemaMessageOverridesDeliveryReceiptPolicy.DEFAULT,
                    ),
                ),
            ),
        )
        val outgoingDistListMessageState = getMessageState(
            conversationId = LibthreemaConversationId.DistributionList(2u),
            creatorIdentity = myIdentity,
            direction = LibthreemaMessageDirection.Outgoing(
                LibthreemaOutgoingMessageDirection(
                    sentAt = null,
                    deliveredAt = null,
                ),
            ),
        )
        val messageStateConverter = getMessageStateConverter()

        // act
        val incomingMessageStateConversionResult = messageStateConverter.convertDistributionListMessage(incomingDistListMessageState)
        val outgoingMessageStateConversionResult = messageStateConverter.convertDistributionListMessage(outgoingDistListMessageState)

        // assert
        assertNull(incomingMessageStateConversionResult.messageModel.identity)
        assertNull(outgoingMessageStateConversionResult.messageModel.identity)
    }

    private fun getMessageStateConverter(
        messageBodyStateConverter: MessageBodyStateConverter = mockk {
            every { convertToMessageBodyAndRevisions(any()) } returns getExampleMessageBodyAndRevisions()
        },
        uuidGenerator: UUIDGenerator = UUIDGenerator {
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        },
    ) = MessageStateConverter(
        messageBodyStateConverter = messageBodyStateConverter,
        uuidGenerator = uuidGenerator,
        timeProvider = TestTimeProvider(),
    )

    private fun getMessageState(
        conversationId: LibthreemaConversationId,
        creatorIdentity: IdentityString,
        messageId: ULong = 42u,
        createdAt: ULong = 0u,
        direction: LibthreemaMessageDirection = LibthreemaMessageDirection.Incoming(
            v1 = LibthreemaIncomingMessageDirection(
                receivedAt = 1u,
                overrides = LibthreemaMessageOverrides(
                    deliveryReceiptPolicy = LibthreemaMessageOverridesDeliveryReceiptPolicy.DEFAULT,
                ),
            ),
        ),
        readAt: ULong? = null,
        starMarker: LibthreemaMessageStarMarker = LibthreemaMessageStarMarker.NONE,
        reactions: List<LibthreemaMessageReaction> = emptyList(),
        body: LibthreemaMessageStateBody = mockk(),
    ) = LibthreemaMessageState(
        conversationId = conversationId,
        creatorIdentity = creatorIdentity,
        messageId = messageId,
        createdAt = createdAt,
        direction = direction,
        readAt = readAt,
        starMarker = starMarker,
        reactions = reactions,
        body = body,
    )

    private fun getExampleMessageBodyAndRevisions() = MessageBodyAndRevisions(
        messageType = MessageType.LOCATION,
        originalBody = "body",
    )
}
