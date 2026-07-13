package ch.threema.app.tasks

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.poll.GroupPollVoteMessage
import ch.threema.domain.protocol.csp.messages.poll.PollId
import ch.threema.domain.protocol.csp.messages.poll.PollVote
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.poll.PollModel
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("OutgoingPollVoteGroupMessageTask")

class OutgoingPollVoteGroupMessageTask(
    private val messageId: MessageId,
    private val recipientIdentities: Set<IdentityString>,
    private val pollId: PollId,
    private val pollCreator: String,
    private val pollVotes: Array<PollVote>,
    private val pollType: PollModel.Type,
    private val apiGroupId: GroupId,
    private val groupCreator: String,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingPollVoteGroupMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        if (pollType == PollModel.Type.RESULT_ON_CLOSE) {
            sendPollVote(handle, setOf(pollCreator))
        } else {
            sendPollVote(handle, recipientIdentities)
        }
    }

    private suspend fun sendPollVote(handle: ActiveTaskCodec, recipients: Set<String>) {
        val group = groupService.getByApiGroupIdAndCreator(apiGroupId, groupCreator)

        if (group == null) {
            logger.error(
                "Cannot find group model for id {} with creator {}",
                apiGroupId,
                groupCreator,
            )
            return
        }

        sendGroupMessage(
            group,
            recipients,
            null,
            Instant.now(),
            messageId,
            { createMessage() },
            handle,
        )
    }

    private fun createMessage() = GroupPollVoteMessage().also {
        it.pollCreatorIdentity = pollCreator
        it.pollId = pollId
        it.addVotes(pollVotes.toList())
    }

    override fun serialize(): SerializableTaskData = OutgoingPollVoteGroupMessageData(
        messageId.toString(),
        recipientIdentities,
        pollId.pollId,
        pollCreator,
        pollVotes.map { Pair(it.id, it.value) },
        pollType,
        apiGroupId.toString(),
        groupCreator,
    )

    @Serializable
    class OutgoingPollVoteGroupMessageData(
        private val messageId: String,
        private val recipientIdentities: Set<IdentityString>,
        @SerialName("ballotId")
        private val pollId: ByteArray,
        @SerialName("ballotCreator")
        private val pollCreator: String,
        @SerialName("ballotVotes")
        private val pollVotes: List<Pair<Int, Int>>,
        @SerialName("ballotType")
        private val pollType: PollModel.Type,
        private val apiGroupId: String,
        private val groupCreator: String,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingPollVoteGroupMessageTask(
                messageId = MessageId.fromString(messageId),
                recipientIdentities = recipientIdentities,
                pollId = PollId(pollId),
                pollCreator = pollCreator,
                pollVotes = pollVotes.map {
                    PollVote(it.first, it.second)
                }.toTypedArray(),
                pollType = pollType,
                apiGroupId = GroupId(apiGroupId),
                groupCreator = groupCreator,
            )
    }
}
