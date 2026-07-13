package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.poll.PollService
import ch.threema.app.utils.PollUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.poll.PollData
import ch.threema.domain.protocol.csp.messages.poll.PollDataChoice
import ch.threema.domain.protocol.csp.messages.poll.PollId
import ch.threema.domain.protocol.csp.messages.poll.PollSetupInterface
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.poll.PollChoiceModel
import ch.threema.storage.models.poll.PollModel

private val logger = getThreemaLogger("ReflectedOutgoingPollUtils")

fun handleReflectedOutgoingPoll(
    pollSetupMessage: PollSetupInterface,
    messageId: MessageId,
    messageReceiver: MessageReceiver<*>,
    pollService: PollService,
) {
    val pollId = pollSetupMessage.pollId ?: run {
        logger.warn("Received poll setup message without id")
        return
    }

    val pollData = pollSetupMessage.pollData ?: run {
        logger.warn("Received poll setup message without data")
        return
    }

    when (pollData.state) {
        PollData.State.OPEN -> handleReflectedOutgoingOpenPoll(
            pollId,
            pollData,
            messageId,
            messageReceiver,
        )

        PollData.State.CLOSED -> handleReflectedOutgoingClosedPoll(
            pollId,
            pollSetupMessage.pollCreatorIdentity,
            pollService,
            messageId,
        )

        null -> logger.warn("Received poll setup message where state is null")
    }
}

private fun handleReflectedOutgoingOpenPoll(
    pollId: PollId,
    pollData: PollData,
    messageId: MessageId,
    messageReceiver: MessageReceiver<*>,
) {
    PollUtil.createPoll(
        messageReceiver,
        pollData.description,
        pollData.type.toModelType(),
        pollData.assessmentType.toModelType(),
        pollData.choiceList.map(PollDataChoice::toPollChoiceModel),
        pollId,
        messageId,
        TriggerSource.SYNC,
    ) ?: run {
        logger.error("Poll model is null")
        return
    }
}

private fun handleReflectedOutgoingClosedPoll(
    pollId: PollId,
    pollCreatorIdentity: IdentityString?,
    pollService: PollService,
    messageId: MessageId,
) {
    val pollModel = pollService[pollId.toString(), pollCreatorIdentity] ?: run {
        logger.error(
            "Poll model not found for id {} and creator {}",
            pollId,
            pollCreatorIdentity,
        )
        return
    }

    PollUtil.closePoll(
        null,
        pollModel,
        pollService,
        messageId,
        TriggerSource.SYNC,
    )
}

private fun PollData.Type.toModelType(): PollModel.Type = when (this) {
    PollData.Type.RESULT_ON_CLOSE -> PollModel.Type.RESULT_ON_CLOSE
    PollData.Type.INTERMEDIATE -> PollModel.Type.INTERMEDIATE
}

private fun PollData.AssessmentType.toModelType(): PollModel.Assessment = when (this) {
    PollData.AssessmentType.SINGLE -> PollModel.Assessment.SINGLE_CHOICE
    PollData.AssessmentType.MULTIPLE -> PollModel.Assessment.MULTIPLE_CHOICE
}

private fun PollDataChoice.toPollChoiceModel(): PollChoiceModel = PollChoiceModel()
    .setName(this.name)
    .setOrder(this.order)
    .setVoteCount(this.totalVotes)
    .setApiPollChoiceId(this.id)
