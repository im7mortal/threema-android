package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.protocolsteps.CommonEditMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ContactMessageIdentifier
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectedOutgoingEditMessageTask")

internal class ReflectedOutgoingEditMessageTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<EditMessage>(
    outgoingMessage = outgoingMessage,
    message = EditMessage.fromReflected(outgoingMessage),
    type = CspE2eMessageType.EDIT_MESSAGE,
    serviceManager = serviceManager,
),
    KoinComponent {

    private val commonEditMessageReceiveSteps: CommonEditMessageReceiveSteps by inject()
    private val identityProvider: IdentityProvider by inject()

    override fun processOutgoingMessage() {
        val userIdentity = identityProvider.requireIdentityString()
        val messageReceiverIdentity = messageReceiver.contactModel?.identity
        if (messageReceiverIdentity == null) {
            logger.error("Missing contact message receiver identity, discarding")
            return
        }
        val result = commonEditMessageReceiveSteps.run(
            editMessageSenderIdentity = userIdentity,
            editMessageText = message.data.text,
            editMessageCreatedAt = message.timestamp,
            referredMessageIdentifier = ContactMessageIdentifier(
                messageId = MessageId(message.data.messageId),
                identity = messageReceiverIdentity,
            ),
        )

        // Note that it is irrelevant for reflected messages whether they should be discarded or not. We just log it for debugging reasons.
        when (result) {
            ReceiveStepsResult.SUCCESS -> logger.info("Successfully processed reflected outgoing edit message")
            ReceiveStepsResult.DISCARD -> logger.info("Discarded reflected outgoing edit message")
        }
    }
}
