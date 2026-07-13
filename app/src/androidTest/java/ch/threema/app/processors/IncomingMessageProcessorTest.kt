package ch.threema.app.processors

import ch.threema.app.DangerousTest
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGREAD
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGRECEIVED
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK
import ch.threema.domain.protocol.csp.ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.protocol.csp.messages.TypingIndicatorMessage
import ch.threema.domain.protocol.csp.messages.location.LocationMessage
import ch.threema.domain.protocol.csp.messages.location.LocationMessageData
import ch.threema.domain.protocol.csp.messages.poll.PollData
import ch.threema.domain.protocol.csp.messages.poll.PollDataChoice
import ch.threema.domain.protocol.csp.messages.poll.PollDataChoiceBuilder
import ch.threema.domain.protocol.csp.messages.poll.PollId
import ch.threema.domain.protocol.csp.messages.poll.PollSetupMessage
import ch.threema.domain.protocol.csp.messages.poll.PollVote
import ch.threema.domain.protocol.csp.messages.poll.PollVoteMessage
import java.time.Instant
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

@DangerousTest
class IncomingMessageProcessorTest : MessageProcessorProvider() {
    @Test
    fun testIncomingTextMessage() = runTest {
        assertSuccessfulMessageProcessing(
            TextMessage().also { it.text = "Hello!" }.enrich(),
            contactA,
        )
    }

    @Test
    fun testIncomingLocationMessage() = runTest {
        val locationMessageData = LocationMessageData(
            latitude = 0.0,
            longitude = 0.0,
            accuracy = null,
            poi = null,
        )
        assertSuccessfulMessageProcessing(
            message = LocationMessage(locationMessageData = locationMessageData).enrich(),
            fromContact = contactA,
        )
    }

    @Test
    fun testIncomingPoll() = runTest {
        val pollId = PollId()
        val pollCreator = contactA.identity

        val pollData = PollData().also { data ->
            data.description = "This describes the poll!"
            data.assessmentType = PollData.AssessmentType.SINGLE
            data.type = PollData.Type.INTERMEDIATE
            List<PollDataChoice>(10) { index ->
                PollDataChoiceBuilder()
                    .setId(index)
                    .setDescription("This is choice $index!")
                    .setSortKey(index)
                    .build()
            }.forEach { data.addChoice(it) }
            data.displayType = PollData.DisplayType.LIST_MODE
            data.state = PollData.State.OPEN
        }

        val pollSetupMessage = PollSetupMessage().also {
            it.pollCreatorIdentity = pollCreator
            it.pollId = pollId
            it.pollData = pollData
        }.enrich()

        // Test a valid poll setup message that opens a poll
        assertSuccessfulMessageProcessing(pollSetupMessage, contactA)

        val pollVoteMessage = PollVoteMessage().also { voteMessage ->
            voteMessage.pollId = pollId
            voteMessage.pollCreatorIdentity = pollCreator
            voteMessage.votes.addAll(
                List(5) { index ->
                    PollVote(index, 0)
                },
            )
        }.enrich()

        assertSuccessfulMessageProcessing(pollVoteMessage, contactA)
    }

    @Test
    fun testIncomingDeliveryReceipt() = runTest {
        val messageId = MessageId.random()

        // Test 'received'
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGRECEIVED
                it.receiptMessageIds = arrayOf(messageId)
                it.messageId = MessageId(0)
            }.enrich(),
            contactA,
        )

        // Test 'read'
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGREAD
                it.receiptMessageIds = arrayOf(messageId)
            }.enrich(),
            contactA,
        )

        // Test 'userack'
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGUSERACK
                it.receiptMessageIds = arrayOf(messageId)
            }.enrich(),
            contactA,
        )

        // Test 'userdec'
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGUSERDEC
                it.receiptMessageIds = arrayOf(messageId)
            }.enrich(),
            contactA,
        )

        // Test 'received' with two times the same message id
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGRECEIVED
                it.receiptMessageIds = arrayOf(messageId, messageId)
                it.messageId = MessageId(0)
            }.enrich(),
            contactA,
        )

        // Test 'received' with many message ids
        assertSuccessfulMessageProcessing(
            DeliveryReceiptMessage().also {
                it.receiptType = DELIVERYRECEIPT_MSGRECEIVED
                it.receiptMessageIds = Array(100) { MessageId.random() }
                it.messageId = MessageId(0)
            }.enrich(),
            contactA,
        )
    }

    @Test
    fun testIncomingTypingIndicator() = runTest {
        assertSuccessfulMessageProcessing(
            TypingIndicatorMessage().also { it.isTyping = true }.enrich(),
            contactA,
        )
        assertSuccessfulMessageProcessing(
            TypingIndicatorMessage().also { it.isTyping = false }.enrich(),
            contactA,
        )
    }

    @Test
    fun testInvalidMessage() = runTest {
        val badMessage = TextMessage().also {
            it.fromIdentity = contactA.identity
            it.toIdentity = myContact.identity
            it.messageId = MessageId.random()
            it.timestamp = Instant.now()
            it.text = "" // Bad message; cannot be decoded due to invalid length
        }

        // Processing the message should not result in a crash, it should just ack the message
        // towards the server, discard it and no delivery receipt should be sent
        processMessage(badMessage, contactA.identityStore)

        // Assert that no messages are sent (also no delivery receipt, as it is an invalid message)
        assertTrue(sentMessagesNewTask.isEmpty())
        assertTrue(sentMessagesInsideTask.isEmpty())
    }

    @Test
    fun testMessageToSomeoneElse() = runTest {
        val messageToB = TextMessage().also {
            it.fromIdentity = contactA.identity
            it.toIdentity = contactB.identity
            it.messageId = MessageId.random()
            it.timestamp = Instant.now()
            it.text = "This message is for contact B!"
        }

        assertFailingMessageProcessing(messageToB, contactA)
    }

    private suspend fun assertSuccessfulMessageProcessing(
        message: AbstractMessage,
        fromContact: TestContact,
    ) {
        val messageId = message.messageId
        processMessage(
            message.also { it.fromIdentity = fromContact.identity },
            fromContact.identityStore,
        )

        val expectDeliveryReceiptSent = message.sendAutomaticDeliveryReceipt() &&
            !message.hasFlag(ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS)

        val sentMessage = sentMessagesNewTask.poll()
        if (expectDeliveryReceiptSent) {
            if (sentMessage is DeliveryReceiptMessage) {
                assertContentEquals(messageId.messageId, sentMessage.receiptMessageIds[0].messageId)
                assertEquals(DELIVERYRECEIPT_MSGRECEIVED, sentMessage.receiptType)
            } else {
                fail("Instead of delivery receipt we got $sentMessage")
            }
        } else if (sentMessage != null) {
            fail("Expected no message but got $sentMessage")
        }

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    private suspend fun assertFailingMessageProcessing(
        message: AbstractMessage,
        fromContact: TestContact,
    ) {
        processMessage(
            message.also { it.fromIdentity = fromContact.identity },
            fromContact.identityStore,
        )

        assertTrue(sentMessagesInsideTask.isEmpty())
        assertTrue(sentMessagesNewTask.isEmpty())
    }

    private fun AbstractMessage.enrich(): AbstractMessage {
        toIdentity = myContact.identity
        timestamp = Instant.now()
        messageId = MessageId.random()
        return this
    }
}
