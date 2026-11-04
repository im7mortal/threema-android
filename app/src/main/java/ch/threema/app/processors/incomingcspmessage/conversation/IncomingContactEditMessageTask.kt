package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.protocolsteps.CommonEditMessageReceiveSteps
import ch.threema.data.datatypes.ContactMessageIdentifier
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class IncomingContactEditMessageTask(
    editMessage: EditMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<EditMessage>(editMessage, triggerSource, serviceManager), KoinComponent {
    private val commonEditMessageReceiveSteps: CommonEditMessageReceiveSteps by inject()

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        return applyEdit()
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        return applyEdit()
    }

    private fun applyEdit(): ReceiveStepsResult =
        commonEditMessageReceiveSteps.run(
            editMessageSenderIdentity = message.fromIdentity,
            editMessageText = message.data.text,
            editMessageCreatedAt = message.timestamp,
            referredMessageIdentifier = ContactMessageIdentifier(
                messageId = MessageId(message.data.messageId),
                identity = message.fromIdentity,
            ),
        )
}
