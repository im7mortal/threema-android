package ch.threema.domain.protocol.csp.messages.poll

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.protobuf.d2d.incomingMessage
import ch.threema.protobuf.d2d.outgoingMessage
import ch.threema.testhelpers.willThrow
import com.google.protobuf.kotlin.toByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

open class PollSetupMessageTest {
    private val fromIdentity = "01234567"

    private val pollSetupMessage = PollSetupMessage().apply {
        pollId = PollId()
        pollCreatorIdentity = "01234567"
        pollData = PollData().apply {
            setDescription("Cool poll")
            setState(PollData.State.OPEN)
            setAssessmentType(PollData.AssessmentType.SINGLE)
            setType(PollData.Type.INTERMEDIATE)
            setChoiceType(PollData.ChoiceType.TEXT)
            addChoice(
                PollDataChoiceBuilder()
                    .setId(0)
                    .setSortKey(0)
                    .setDescription("Coice 1")
                    .build(),
            )
            addChoice(
                PollDataChoiceBuilder()
                    .setId(1)
                    .setSortKey(1)
                    .setDescription("Coice 2")
                    .build(),
            )
            addChoice(
                PollDataChoiceBuilder()
                    .setId(2)
                    .setSortKey(2)
                    .setDescription("Coice 3")
                    .build(),
            )
            addParticipant("01234567")
            setDisplayType(PollData.DisplayType.LIST_MODE)
        }
    }

    private val pollSetupMessageBody: ByteArray = pollSetupMessage.body!!

    @Test
    fun shouldThrowBadMessageExceptionWhenLengthTooShort() {
        // arrange
        val testBlockLazy = {
            // act
            PollSetupMessage.fromByteArray(
                data = pollSetupMessageBody,
                offset = 0,
                length = 0,
                pollCreatorIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetBelowZero() {
        // arrange
        val testBlockLazy = {
            // act
            PollSetupMessage.fromByteArray(
                data = pollSetupMessageBody,
                offset = -1,
                length = 64,
                pollCreatorIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenDataIsShorterThanPassedLength() {
        // arrange
        val testBlockLazy = {
            // act
            PollSetupMessage.fromByteArray(
                data = pollSetupMessageBody,
                offset = 0,
                length = pollSetupMessageBody.size + 1,
                pollCreatorIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenDataIsShorterThanPassedLengthWithOffset() {
        // arrange
        val testBlockLazy = {
            // act
            PollSetupMessage.fromByteArray(
                data = pollSetupMessageBody,
                offset = 1,
                length = pollSetupMessageBody.size,
                pollCreatorIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldDecodeCorrectValuesWithoutOffset() {
        // act
        val resultPollSetupMessage = PollSetupMessage.fromByteArray(
            data = pollSetupMessageBody,
            offset = 0,
            length = pollSetupMessageBody.size,
            pollCreatorIdentity = fromIdentity,
        )

        // assert
        assertPollSetupMessageFields(resultPollSetupMessage)
    }

    @Test
    fun shouldDecodeCorrectValuesWithOffset() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + pollSetupMessageBody

        // act
        val resultPollSetupMessage = PollSetupMessage.fromByteArray(
            data = dataWithOffsetByte,
            offset = 1,
            length = pollSetupMessageBody.size,
            pollCreatorIdentity = fromIdentity,
        )

        // assert
        assertPollSetupMessageFields(resultPollSetupMessage)
    }

    @Test
    fun fromReflectedIncomingShouldParseBodyAndSetCommonFields() {
        // act
        val incomingMessageId = 12345678L
        val incomingMessageCreatedAt: Long = System.currentTimeMillis()
        val incomingD2DMessage = incomingMessage {
            this.senderIdentity = fromIdentity
            this.messageId = incomingMessageId
            this.createdAt = incomingMessageCreatedAt
            this.body = pollSetupMessageBody.toByteString()
        }

        // act
        val resultPollSetupMessage: PollSetupMessage = PollSetupMessage.fromReflected(
            message = incomingD2DMessage,
            pollCreatorIdentity = fromIdentity,
        )

        // assert
        assertEquals(resultPollSetupMessage.messageId, MessageId(incomingMessageId))
        assertEquals(resultPollSetupMessage.timestamp.toEpochMilli(), incomingMessageCreatedAt)
        assertEquals(resultPollSetupMessage.fromIdentity, fromIdentity)
        assertPollSetupMessageFields(resultPollSetupMessage)
    }

    @Test
    fun fromReflectedOutgoingShouldParseBodyAndSetCommonFields() {
        // act
        val outgoingMessageId = MessageId.random()
        val outgoingMessageCreatedAt: Long = 42424242
        val outgoingD2DMessage = outgoingMessage {
            this.messageId = outgoingMessageId.messageIdLong
            this.createdAt = outgoingMessageCreatedAt
            this.body = pollSetupMessageBody.toByteString()
        }

        // act
        val resultPollSetupMessage: PollSetupMessage = PollSetupMessage.fromReflected(
            message = outgoingD2DMessage,
            pollCreatorIdentity = fromIdentity,
        )

        // assert
        assertEquals(resultPollSetupMessage.messageId, outgoingMessageId)
        assertEquals(resultPollSetupMessage.timestamp.toEpochMilli(), outgoingMessageCreatedAt)
        assertPollSetupMessageFields(resultPollSetupMessage)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetNotPassedCorrectly() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + pollSetupMessageBody

        val testBlockLazy = {
            // act
            PollSetupMessage.fromByteArray(
                data = dataWithOffsetByte,
                offset = 0,
                length = pollSetupMessageBody.size,
                pollCreatorIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    private fun assertPollSetupMessageFields(actual: PollSetupMessage?) {
        if (actual?.pollData == null) {
            fail()
        }
        assertEquals(pollSetupMessage.pollId, actual.pollId)
        assertEquals(pollSetupMessage.pollCreatorIdentity, actual.pollCreatorIdentity)
        assertEquals(pollSetupMessage.pollData!!.description, actual.pollData!!.description)
        assertEquals(pollSetupMessage.pollData!!.state, actual.pollData!!.state)
        assertEquals(
            pollSetupMessage.pollData!!.assessmentType,
            actual.pollData!!.assessmentType,
        )
        assertEquals(pollSetupMessage.pollData!!.type, actual.pollData!!.type)
        assertEquals(pollSetupMessage.pollData!!.choiceType, actual.pollData!!.choiceType)
        assertContentEquals(
            pollSetupMessage.pollData!!.participants,
            actual.pollData!!.participants,
        )
        assertEquals(pollSetupMessage.pollData!!.displayType, actual.pollData!!.displayType)
        assertEquals(
            pollSetupMessage.pollData!!.choiceList.size,
            actual.pollData!!.choiceList.size,
        )
        pollSetupMessage.pollData!!.choiceList.forEachIndexed { index, value ->
            assertEquals(value.id, actual.pollData!!.choiceList[index].id)
            assertEquals(value.name, actual.pollData!!.choiceList[index].name)
            assertEquals(value.order, actual.pollData!!.choiceList[index].order)
            assertContentEquals(
                value.pollDataChoiceResults,
                actual.pollData!!.choiceList[index].pollDataChoiceResults,
            )
            assertEquals(value.totalVotes, actual.pollData!!.choiceList[index].totalVotes)
        }
    }
}
