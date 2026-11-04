package ch.threema.app.protocolsteps

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.MessageEvent.MessageEdited
import ch.threema.app.eventbus.events.MessageEvent.MessagesUpdated
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.services.MessageService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ContactMessageIdentifier
import ch.threema.data.datatypes.DistributionListMessageIdentifier
import ch.threema.data.datatypes.EditHistoryType
import ch.threema.data.datatypes.GroupMessageIdentifier
import ch.threema.data.datatypes.MessageIdentifier
import ch.threema.data.repositories.EditHistoryEntryCreateException
import ch.threema.data.repositories.EditHistoryRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.GroupId
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import java.time.Instant

private val logger = getThreemaLogger("CommonEditMessageReceiveSteps")

/**
 * These steps should be run to apply a received edit message.
 *
 * Note that these steps are also applied if it is a reflected message and therefore the referred message may also be an outbound message.
 */
class CommonEditMessageReceiveSteps(
    private val messageService: MessageService,
    private val groupModelRepository: GroupModelRepository,
    private val editHistoryRepository: EditHistoryRepository,
    private val identityProvider: IdentityProvider,
    private val globalEventBuses: GlobalEventBuses,
    private val timeProvider: TimeProvider,
) {
    /**
     * Run the common edit message receive steps. They apply an inbound edit message and return [ReceiveStepsResult].
     */
    fun run(
        editMessageSenderIdentity: IdentityString,
        editMessageText: String,
        editMessageCreatedAt: Instant,
        referredMessageIdentifier: MessageIdentifier,
    ): ReceiveStepsResult {
        // Look up the message.
        val referredMessage = when (referredMessageIdentifier) {
            is ContactMessageIdentifier -> messageService.getContactMessageModel(
                referredMessageIdentifier.messageId,
                referredMessageIdentifier.identity,
            )

            is GroupMessageIdentifier -> {
                val groupIdentity = groupModelRepository.getByGroupDatabaseId(referredMessageIdentifier.groupDatabaseId)
                    ?.groupIdentity
                    ?: return ReceiveStepsResult.DISCARD
                messageService.getGroupMessageModel(
                    referredMessageIdentifier.messageId,
                    groupIdentity.creatorIdentity,
                    GroupId(groupIdentity.groupId),
                )
            }

            is DistributionListMessageIdentifier -> return ReceiveStepsResult.DISCARD
        }

        // If the referred message is not defined, abort these steps.
        if (referredMessage == null) {
            logger.info("Incoming Edit Message: No message found for id: {}", referredMessage)
            // Note that this is a totally legitimate scenario if the user has locally deleted the message. Therefore, we still return success, as we
            // still need to reflect the message as it may still exist on linked devices.
            return ReceiveStepsResult.SUCCESS
        }

        // If the sender is not the original sender of the message, discard the message
        val originalMessageSender = referredMessage.getSenderIdentity()
        if (editMessageSenderIdentity != originalMessageSender) {
            logger.warn(
                "Incoming Edit Message: original message's sender {} does not equal edit-message's sender {}",
                originalMessageSender,
                editMessageSenderIdentity,
            )
            return ReceiveStepsResult.DISCARD
        }

        // If the referred message is not editable, discard the message
        if (referredMessage.type?.canBeEdited != true) {
            logger.warn("Incoming Edit Message: Message of type {} cannot be edited", referredMessage.type)
            return ReceiveStepsResult.DISCARD
        }

        // Edit the message
        val previousText = referredMessage.getPreviousText() ?: return ReceiveStepsResult.DISCARD
        val previousTextCreatedAt = referredMessage.getPreviousTextCreatedAt()

        referredMessage.setUpdatedText(editMessageText)
        referredMessage.editedAt = editMessageCreatedAt

        val messageUid = referredMessage.uid ?: run {
            logger.error("Could not edit message because uid is null")
            // Note that even if we failed to edit the message, we return success so that it will still be reflected.
            return ReceiveStepsResult.SUCCESS
        }

        try {
            editHistoryRepository.createEntry(
                messageUid = messageUid,
                text = previousText,
                createdAt = previousTextCreatedAt,
                editHistoryType = when (referredMessageIdentifier) {
                    is ContactMessageIdentifier -> EditHistoryType.CONTACT
                    is GroupMessageIdentifier -> EditHistoryType.GROUP
                },
                localMessageId = referredMessage.id,
            )
        } catch (e: EditHistoryEntryCreateException) {
            logger.error("Unable to save edit message text", e)
        }
        messageService.save(referredMessage)
        globalEventBuses.messages.emit(MessagesUpdated(referredMessage))
        globalEventBuses.messages.emit(MessageEdited(referredMessage))

        return ReceiveStepsResult.SUCCESS
    }

    private fun MessageModel.getSenderIdentity(): IdentityString =
        if (isOutbox) {
            identityProvider.requireIdentityString()
        } else {
            identity ?: error("Identity of message is not set")
        }

    private fun MessageModel.getPreviousText(): String? = when (type) {
        MessageType.TEXT -> body ?: ""
        MessageType.FILE -> caption ?: ""
        else -> null
    }

    private fun MessageModel.getPreviousTextCreatedAt(): Instant =
        editedAt ?: createdAt ?: timeProvider.get()

    private fun MessageModel.setUpdatedText(text: String) = when (type) {
        MessageType.TEXT -> body = text
        MessageType.FILE -> {
            caption = text
            fileData.caption = text
            body = fileData.toString()
        }

        else -> logger.error("Cannot set updated text for message of type {}", type)
    }
}
