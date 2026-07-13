package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageState
import java.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectedOutgoingDeliveryReceiptTask")

internal class ReflectedOutgoingDeliveryReceiptTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<DeliveryReceiptMessage>(
    outgoingMessage = outgoingMessage,
    message = DeliveryReceiptMessage.fromReflected(outgoingMessage),
    type = CspE2eMessageType.DELIVERY_RECEIPT,
    serviceManager = serviceManager,
),
    KoinComponent {
    private val globalEventBuses: GlobalEventBuses by inject()
    private val messageService by lazy { serviceManager.messageService }
    private val notificationService by lazy { serviceManager.notificationService }
    private val myIdentity by lazy { serviceManager.identityStore.getIdentityString()!! }

    override fun processOutgoingMessage() {
        logger.info("Processing reflected outgoing delivery receipt")

        val deliveryReceiptMessage = DeliveryReceiptMessage.fromReflected(outgoingMessage)
        val state = MessageUtil.receiptTypeToMessageState(deliveryReceiptMessage.receiptType)

        if (state == null) {
            logger.warn("Message {} error: unknown delivery receipt type", outgoingMessage.messageId)
            return
        }

        val identity = outgoingMessage.conversation.contact

        for (messageId in deliveryReceiptMessage.receiptMessageIds) {
            val messageModel = messageService.getContactMessageModel(messageId, identity)
            if (messageModel == null) {
                logger.warn(
                    "Message model ({}) for reflected outgoing delivery receipt is null",
                    messageId,
                )
                continue
            }

            updateMessage(messageModel, state)

            if (state == MessageState.READ) {
                notificationService.cancel(ContactConversationId(identity))
            }
        }
    }

    private fun updateMessage(messageModel: AbstractMessageModel, state: MessageState) {
        if (MessageUtil.isReaction(state)) {
            messageService.addMessageReaction(
                messageModel,
                state,
                // the identity that reacted (this is us => reflected outgoing message)
                myIdentity,
                Instant.ofEpochMilli(outgoingMessage.createdAt),
            )
        } else {
            when (state) {
                MessageState.DELIVERED -> {
                    val date = Instant.ofEpochMilli(outgoingMessage.createdAt)
                    // The delivered at date is stored in created at for incoming messages
                    messageModel.createdAt = date
                    messageModel.modifiedAt = date
                    messageService.save(messageModel)
                    globalEventBuses.messages.emit(MessageEvent.MessagesUpdated(messageModel))
                }

                MessageState.READ -> {
                    val date = Instant.ofEpochMilli(outgoingMessage.createdAt)
                    messageModel.readAt = date
                    messageModel.modifiedAt = date
                    messageModel.isRead = true
                    messageService.save(messageModel)
                    globalEventBuses.messages.emit(MessageEvent.MessagesUpdated(messageModel))
                }

                else -> logger.error("Unsupported delivery receipt reflected of state {}", state)
            }
        }
    }
}
