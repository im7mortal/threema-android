package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.protocolsteps.CommonEditMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.GroupMessageIdentifier
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectedOutgoingGroupEditMessageTask")

internal class ReflectedOutgoingGroupEditMessageTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupEditMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupEditMessage.fromReflected(outgoingMessage),
    type = CspE2eMessageType.GROUP_EDIT_MESSAGE,
    serviceManager = serviceManager,
),
    KoinComponent {

    private val identityProvider: IdentityProvider by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val commonEditMessageReceiveSteps: CommonEditMessageReceiveSteps by inject()

    override fun processOutgoingMessage() {
        val userIdentity = identityProvider.requireIdentityString()
        val groupModel = requireGroupModel()
        val result = commonEditMessageReceiveSteps.run(
            editMessageSenderIdentity = userIdentity,
            editMessageText = message.data.text,
            editMessageCreatedAt = message.timestamp,
            referredMessageIdentifier = GroupMessageIdentifier(
                messageId = MessageId(message.data.messageId),
                groupDatabaseId = groupModel.getDatabaseId(),
            ),
        )

        // Note that it is irrelevant for reflected messages whether they should be discarded or not. We just log it for debugging reasons.
        when (result) {
            ReceiveStepsResult.SUCCESS -> logger.info("Successfully processed reflected outgoing group edit message")
            ReceiveStepsResult.DISCARD -> logger.info("Discarded reflected outgoing group edit message")
        }
    }

    private fun requireGroupModel(): GroupModel {
        check(outgoingMessage.conversation.hasGroup()) { "The message does not have a group identity set" }

        val groupModel = groupModelRepository.getByCreatorIdentityAndId(
            creatorIdentity = outgoingMessage.conversation.group.creatorIdentity,
            groupId = GroupId(outgoingMessage.conversation.group.groupId),
        )
        checkNotNull(groupModel) { "The reflected message does not belong to an existing group" }

        return groupModel
    }
}
