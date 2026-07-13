package ch.threema.app.webclient.services.instance.message.updater

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.app.webclient.Protocol
import ch.threema.app.webclient.converter.Conversation
import ch.threema.app.webclient.converter.MsgpackObjectBuilder
import ch.threema.app.webclient.exceptions.ConversionException
import ch.threema.app.webclient.services.instance.MessageDispatcher
import ch.threema.app.webclient.services.instance.MessageUpdater
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.storage.models.ConversationModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.msgpack.core.MessagePackException

private val logger = getThreemaLogger("ConversationUpdateHandler")

@WorkerThread
class ConversationUpdateHandler @AnyThread constructor(
    private val handler: HandlerExecutor,
    private val updateDispatcher: MessageDispatcher,
    private val conversationCategoryService: ConversationCategoryService,
) : MessageUpdater(Protocol.SUB_TYPE_CONVERSATION), KoinComponent {
    private val globalEventFlows: GlobalEventFlows by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private var coroutineScope: CoroutineScope? = null

    override fun register() {
        coroutineScope?.cancel()
        coroutineScope = CoroutineScope(dispatcherProvider.worker)

        coroutineScope?.launch {
            globalEventFlows.conversations.collect { event ->
                when (event) {
                    is ConversationEvent.NewConversation -> onNew(event.conversation)
                    is ConversationEvent.ConversationUpdated -> onModified(event.conversation)
                    is ConversationEvent.ConversationRemoved -> onRemoved(event.conversation)
                    is ConversationEvent.ConversationArchived,
                    is ConversationEvent.ConversationDeleted,
                    ConversationEvent.AllConversationsUpdated,
                    -> Unit
                }
            }
        }
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    override fun unregister() {
        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun onNew(conversationModel: ConversationModel) {
        logger.info("Conversation created, sending update to Threema Web (conversation={})", conversationModel.id)
        handler.post {
            respond(conversationModel, Protocol.ARGUMENT_MODE_NEW)
        }
    }

    private fun onModified(modifiedConversationModel: ConversationModel) {
        logger.info("Conversation modified, sending update to Threema Web (conversation={})", modifiedConversationModel.id)
        handler.post {
            respond(modifiedConversationModel, Protocol.ARGUMENT_MODE_MODIFIED)
        }
    }

    private fun onRemoved(conversationModel: ConversationModel) {
        logger.info("Conversation removed, sending update to Threema Web (conversation={})", conversationModel.id)
        handler.post {
            respond(conversationModel, Protocol.ARGUMENT_MODE_REMOVED)
        }
    }

    private fun respond(conversationModel: ConversationModel, mode: String?) {
        if (conversationCategoryService.isMarkedAsPrivate(conversationId = conversationModel.id)) {
            logger.debug("Don't send updates for a private conversation")
            return
        }

        try {
            val args = MsgpackObjectBuilder()
                .put(Protocol.ARGUMENT_MODE, mode)
            val data = Conversation.convert(conversationModel)
            logger.debug("Sending conversation update ({} mode {})", conversationModel.id, mode)
            send(updateDispatcher, data, args)
        } catch (e: ConversionException) {
            logger.error("Exception", e)
        } catch (e: MessagePackException) {
            logger.error("Exception", e)
        }
    }
}
