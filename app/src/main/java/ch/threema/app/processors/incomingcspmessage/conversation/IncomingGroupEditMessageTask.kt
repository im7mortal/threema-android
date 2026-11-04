package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.protocolsteps.CommonEditMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.GroupMessageIdentifier
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("IncomingGroupEditMessageTask")

class IncomingGroupEditMessageTask(
    editMessage: GroupEditMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupEditMessage>(editMessage, triggerSource, serviceManager), KoinComponent {
    private val groupModelRepository: GroupModelRepository by inject()
    private val commonEditMessageReceiveSteps: CommonEditMessageReceiveSteps by inject()

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        val groupModel = runCommonGroupReceiveSteps(message, handle, serviceManager)
            ?: return ReceiveStepsResult.DISCARD

        return applyEdit(groupModel)
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        val groupModel = groupModelRepository.getByCreatorIdentityAndId(
            message.groupCreator,
            message.apiGroupId,
        )

        if (groupModel == null) {
            logger.error("Received a reflected group edit message in an unknown group")
            return ReceiveStepsResult.DISCARD
        }

        return applyEdit(groupModel)
    }

    private fun applyEdit(groupModel: GroupModel): ReceiveStepsResult =
        commonEditMessageReceiveSteps.run(
            editMessageSenderIdentity = message.fromIdentity,
            editMessageText = message.data.text,
            editMessageCreatedAt = message.timestamp,
            referredMessageIdentifier = GroupMessageIdentifier(
                messageId = MessageId(message.data.messageId),
                groupDatabaseId = groupModel.getDatabaseId(),
            ),
        )
}
