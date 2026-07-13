package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.protocol.csp.messages.poll.PollVoteMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage

internal class ReflectedOutgoingPollVoteMessageTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<PollVoteMessage>(
    outgoingMessage = outgoingMessage,
    message = PollVoteMessage.fromReflected(outgoingMessage).apply {
        // This property is used for the poll service to determine who sent the vote.
        fromIdentity = serviceManager.identityStore.getIdentityString()!!
    },
    type = CspE2eMessageType.POLL_VOTE,
    serviceManager = serviceManager,
) {
    private val pollService by lazy { serviceManager.pollService }

    override fun processOutgoingMessage() {
        pollService.vote(message)
    }
}
