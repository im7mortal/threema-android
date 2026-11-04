package ch.threema.app.routines

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.ConversationService
import ch.threema.app.services.MessageService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.utils.MessageUtil
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.getConversationId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("MarkAsReadRoutine")

class MarkAsReadRoutine @JvmOverloads constructor(
    private val conversationService: ConversationService? = ServiceManager.get()?.conversationService,
    private val messageService: MessageService,
    private val notificationService: NotificationService,
) : KoinComponent {
    private val globalEventBuses: GlobalEventBuses by inject()
    private val emojiReactionsRepository: EmojiReactionsRepository by inject()

    /**
     * Marks all the [messages] as read, as well as the corresponding conversation, by resetting its unread counter
     * and removing the 'unread' tag. Additionally, all unread emoji reactions of the conversation are marked as read.
     * If there are notifications for these messages, they are dismissed.
     */
    fun run(
        messages: List<MessageModel>?,
        messageReceiver: MessageReceiver<*>? = null,
    ): Boolean {
        logger.debug("MarkAsReadRoutine.run()")
        var success = true
        val modifiedMessageModels = mutableListOf<MessageModel>()

        if (!messages.isNullOrEmpty()) {
            // It is possible that the list gets modified while we're iterating. In that case,
            // we repeat the operation on exception (to avoid the overhead of copying the whole
            // list before we start).
            repeatOnException {
                success = true
                messages.forEach { messageModel ->
                    if (MessageUtil.canMarkAsRead(messageModel)) {
                        try {
                            if (messageService.markAsRead(messageModel, true)) {
                                modifiedMessageModels.add(messageModel)
                            }
                        } catch (e: ThreemaException) {
                            logger.error("Exception", e)
                            success = false
                        }
                    }
                }
            }
        }

        if (messageReceiver != null) {
            conversationService?.markConversationAsRead(messageReceiver)
        } else {
            messages?.firstOrNull()?.let { message ->
                conversationService?.markConversationAsRead(message)
            }
        }

        markConversationReactionsAsRead(messages, messageReceiver)

        if (modifiedMessageModels.isNotEmpty()) {
            globalEventBuses.messages.emit(MessageEvent.MessagesUpdated(modifiedMessageModels))
            val conversationIds = modifiedMessageModels.map(MessageModel::getConversationId).toSet()
            notificationService.cancelAllConversationNotifications(conversationIds)
        }

        return success
    }

    private fun markConversationReactionsAsRead(
        messages: List<MessageModel>?,
        messageReceiver: MessageReceiver<*>?,
    ) {
        val conversationId: ConversationId = messageReceiver?.conversationId
            ?: messages?.firstOrNull()?.getConversationId()
            ?: return
        try {
            emojiReactionsRepository.markConversationReactionsAsRead(conversationId)
        } catch (e: Exception) {
            logger.error("Failed to mark reactions of conversation {} as read", conversationId, e)
        }
    }

    private fun repeatOnException(maxTries: Int = 10, block: () -> Unit) {
        repeat(maxTries) {
            try {
                block()
                return
            } catch (e: ConcurrentModificationException) {
                logger.error("Failed to mark message as read", e)
            }
        }
    }

    @JvmOverloads
    fun runAsync(
        messages: List<MessageModel>?,
        messageReceiver: MessageReceiver<*>? = null,
        onSuccess: () -> Unit = {},
    ) {
        Thread {
            val success = run(messages, messageReceiver)
            if (success) {
                onSuccess()
            }
        }.start()
    }
}
