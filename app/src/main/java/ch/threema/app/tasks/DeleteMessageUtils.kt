package ch.threema.app.tasks

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.MessageService
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.AbstractMessageModel
import java.time.Instant

private val logger = getThreemaLogger("DeleteMessageUtils")

fun runCommonDeleteMessageReceiveSteps(
    myIdentity: IdentityString,
    deleteMessageSenderIdentity: IdentityString,
    deleteMessageCreatedAt: Instant,
    messageId: Long,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? {
    logger.info(
        "Delete Message: deletion disabled, ignoring delete request for message {} from {}",
        MessageId(messageId),
        deleteMessageSenderIdentity,
    )
    return null
}
