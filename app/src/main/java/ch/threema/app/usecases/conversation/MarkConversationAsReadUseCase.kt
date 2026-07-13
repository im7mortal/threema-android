package ch.threema.app.usecases.conversation

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.routines.MarkAsReadRoutine
import ch.threema.app.services.ConversationService
import ch.threema.app.services.MessageService
import ch.threema.app.services.notification.NotificationService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationId
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ConversationModel
import java.sql.SQLException
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("MarkConversationAsReadUseCase")

class MarkConversationAsReadUseCase(
    private val conversationService: ConversationService,
    private val messageService: MessageService,
    private val notificationService: NotificationService,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun call(conversationId: ConversationId) = withContext(dispatcherProvider.io) {
        val conversationModel: ConversationModel = conversationService.get(conversationId)
            ?: return@withContext
        val messageReceiver: MessageReceiver<*> = conversationModel.messageReceiver
        val unreadMessages: List<AbstractMessageModel> = try {
            messageReceiver.getUnreadMessages()
        } catch (e: SQLException) {
            logger.error("Failed to determine unread messages for conversation {}", conversationId, e)
            return@withContext
        }
        notificationService.cancel(conversationId)
        MarkAsReadRoutine(
            conversationService,
            messageService,
            notificationService,
        ).run(
            unreadMessages,
            messageReceiver,
        )
    }
}
