package ch.threema.app.tasks

import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.IdentityString
import java.time.Instant

class OutgoingVoipCallOfferMessageTask(
    private val voipCallOfferData: VoipCallOfferData,
    private val toIdentity: IdentityString,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingVoipCallOfferMessageTask"

    override val shortLogInfo: String = "cid=${voipCallOfferData.callId}"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = VoipCallOfferMessage()
        message.data = voipCallOfferData

        voipStateService.addRequiredMessageId(voipCallOfferData.callId ?: 0, message.messageId)

        sendContactMessage(
            message = message,
            messageModel = null,
            toIdentity = toIdentity,
            messageId = message.messageId,
            createdAt = Instant.now(),
            handle = handle,
        )

        contactService.bumpLastUpdate(toIdentity)
    }

    // We do not need to persist this message
    override fun serialize(): SerializableTaskData? = null
}
