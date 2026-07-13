package ch.threema.domain.protocol.csp.messages.poll

import ch.threema.domain.models.GroupId
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

open class GroupPollSetupMessageTest {
    private val fromIdentity = "01234567"

    private val groupPollSetupMessage = GroupPollSetupMessage().apply {

        pollId = PollId()
        groupCreator = "01234567"
        apiGroupId = GroupId()
        pollCreatorIdentity = "01234567"

        pollData = PollData().apply {
            this.setDescription("Cool poll")
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
            addParticipant("0Y123456")
            setDisplayType(PollData.DisplayType.LIST_MODE)
        }
    }

    private val groupPollSetupMessageBody: ByteArray = groupPollSetupMessage.body!!

    @Test
    fun shouldThrowBadMessageExceptionWhenLengthTooShort() {
        // arrange
        val testBlockLazy = {
            // act
            GroupPollSetupMessage.fromByteArray(
                data = groupPollSetupMessageBody,
                offset = 0,
                length = 0,
                fromIdentity = fromIdentity,
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
            GroupPollSetupMessage.fromByteArray(
                data = groupPollSetupMessageBody,
                offset = -1,
                length = 64,
                fromIdentity = fromIdentity,
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
            GroupPollSetupMessage.fromByteArray(
                data = groupPollSetupMessageBody,
                offset = 0,
                length = groupPollSetupMessageBody.size + 1,
                fromIdentity = fromIdentity,
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
            GroupPollSetupMessage.fromByteArray(
                data = groupPollSetupMessageBody,
                offset = 1,
                length = groupPollSetupMessageBody.size,
                fromIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    @Test
    fun shouldDecodeCorrectValuesWithoutOffset() {
        // act
        val resultGroupPollSetupMessage = GroupPollSetupMessage.fromByteArray(
            data = groupPollSetupMessageBody,
            offset = 0,
            length = groupPollSetupMessageBody.size,
            fromIdentity = fromIdentity,
        )

        // assert
        assertGroupPollSetupMessageFields(resultGroupPollSetupMessage)
    }

    @Test
    fun shouldDecodeCorrectValuesWithOffset() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + groupPollSetupMessageBody

        // act
        val resultGroupPollSetupMessage = GroupPollSetupMessage.fromByteArray(
            data = dataWithOffsetByte,
            offset = 1,
            length = groupPollSetupMessageBody.size,
            fromIdentity = fromIdentity,
        )

        // assert
        assertGroupPollSetupMessageFields(resultGroupPollSetupMessage)
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
            this.body = groupPollSetupMessageBody.toByteString()
        }

        // act
        val resultGroupPollSetupMessage: GroupPollSetupMessage =
            GroupPollSetupMessage.fromReflected(
                message = incomingD2DMessage,
                fromIdentity = fromIdentity,
            )

        // assert
        assertEquals(resultGroupPollSetupMessage.messageId, MessageId(incomingMessageId))
        assertEquals(resultGroupPollSetupMessage.timestamp.toEpochMilli(), incomingMessageCreatedAt)
        assertEquals(resultGroupPollSetupMessage.fromIdentity, fromIdentity)
        assertGroupPollSetupMessageFields(resultGroupPollSetupMessage)
    }

    @Test
    fun fromReflectedOutgoingShouldParseBodyAndSetCommonFields() {
        // act
        val outgoingMessageId = MessageId.random()
        val outgoingMessageCreatedAt: Long = 42424242
        val outgoingD2DMessage = outgoingMessage {
            this.messageId = outgoingMessageId.messageIdLong
            this.createdAt = outgoingMessageCreatedAt
            this.body = groupPollSetupMessageBody.toByteString()
        }

        // act
        val resultGroupPollSetupMessage: GroupPollSetupMessage =
            GroupPollSetupMessage.fromReflected(
                message = outgoingD2DMessage,
                fromIdentity = fromIdentity,
            )

        // assert
        assertEquals(outgoingMessageId, resultGroupPollSetupMessage.messageId)
        assertEquals(outgoingMessageCreatedAt, resultGroupPollSetupMessage.timestamp.toEpochMilli())
        assertGroupPollSetupMessageFields(resultGroupPollSetupMessage)
    }

    @Test
    fun shouldThrowBadMessageExceptionWhenOffsetNotPassedCorrectly() {
        // arrange
        val dataWithOffsetByte = byteArrayOf(0.toByte()) + groupPollSetupMessageBody

        val testBlockLazy = {
            // act
            GroupPollSetupMessage.fromByteArray(
                data = dataWithOffsetByte,
                offset = 0,
                length = groupPollSetupMessageBody.size,
                fromIdentity = fromIdentity,
            )
        }

        // assert
        testBlockLazy willThrow BadMessageException::class
    }

    private fun assertGroupPollSetupMessageFields(actual: GroupPollSetupMessage?) {
        if (actual?.pollData == null) {
            fail()
        }
        assertEquals(groupPollSetupMessage.pollId, actual.pollId)
        assertEquals(groupPollSetupMessage.groupCreator, actual.groupCreator)
        assertEquals(groupPollSetupMessage.apiGroupId, actual.apiGroupId)
        assertEquals(groupPollSetupMessage.pollCreatorIdentity, actual.pollCreatorIdentity)
        assertEquals(
            groupPollSetupMessage.pollData!!.description,
            actual.pollData!!.description,
        )
        assertEquals(groupPollSetupMessage.pollData!!.state, actual.pollData!!.state)
        assertEquals(
            groupPollSetupMessage.pollData!!.assessmentType,
            actual.pollData!!.assessmentType,
        )
        assertEquals(groupPollSetupMessage.pollData!!.type, actual.pollData!!.type)
        assertEquals(groupPollSetupMessage.pollData!!.choiceType, actual.pollData!!.choiceType)
        assertContentEquals(
            groupPollSetupMessage.pollData!!.participants,
            actual.pollData!!.participants,
        )
        assertEquals(
            groupPollSetupMessage.pollData!!.displayType,
            actual.pollData!!.displayType,
        )
        assertEquals(
            groupPollSetupMessage.pollData!!.choiceList.size,
            actual.pollData!!.choiceList.size,
        )
        groupPollSetupMessage.pollData!!.choiceList.forEachIndexed { index, value ->
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
