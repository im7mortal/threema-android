package ch.threema.app.tasks

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.poll.PollId
import ch.threema.domain.protocol.csp.messages.poll.PollVote
import ch.threema.domain.protocol.csp.messages.poll.PollVoteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OutgoingPollVoteContactMessageTask(
    private val messageId: MessageId,
    private val pollId: PollId,
    private val pollCreator: String,
    private val pollVotes: Array<PollVote>,
    private val toIdentity: IdentityString,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingPollVoteContactMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        // Create the message
        val message = PollVoteMessage().also {
            it.pollCreatorIdentity = pollCreator
            it.pollId = pollId
        }

        // Add all poll votes
        message.addVotes(pollVotes.toList())

        // Send the message
        sendContactMessage(message, null, toIdentity, messageId, Instant.now(), handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingPollVoteContactMessageData(
        messageId.toString(),
        pollId.pollId,
        pollCreator,
        pollVotes.map { Pair(it.id, it.value) },
        toIdentity,
    )

    @Serializable
    class OutgoingPollVoteContactMessageData(
        private val messageId: String,
        @SerialName("ballotId")
        private val pollId: ByteArray,
        @SerialName("ballotCreator")
        private val pollCreator: String,
        @SerialName("ballotVotes")
        private val pollVotes: List<Pair<Int, Int>>,
        private val toIdentity: IdentityString,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingPollVoteContactMessageTask(
                messageId = MessageId.fromString(messageId),
                pollId = PollId(pollId),
                pollCreator = pollCreator,
                pollVotes = pollVotes.map {
                    PollVote(it.first, it.second)
                }.toTypedArray(),
                toIdentity = toIdentity,
            )
    }
}
