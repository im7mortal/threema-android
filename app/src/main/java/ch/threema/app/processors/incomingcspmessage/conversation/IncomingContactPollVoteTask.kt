package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.services.poll.PollVoteResult
import ch.threema.domain.protocol.csp.messages.poll.PollVoteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingContactPollVoteTask(
    private val pollVoteMessage: PollVoteMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<Nothing?>(null, triggerSource, serviceManager) {
    private val pollService = serviceManager.pollService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        processPollVoteMessage()

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult =
        processPollVoteMessage()

    private fun processPollVoteMessage(): ReceiveStepsResult {
        val pollVoteResult: PollVoteResult? = this.pollService.vote(pollVoteMessage)
        return if (pollVoteResult?.isSuccess == true) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }
}
