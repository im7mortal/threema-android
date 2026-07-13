package ch.threema.app.webclient.services.instance.message.updater

import androidx.annotation.AnyThread
import androidx.annotation.StringDef
import androidx.annotation.WorkerThread
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.FileService
import ch.threema.app.utils.MessageUtil
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.app.webclient.Protocol
import ch.threema.app.webclient.converter.Message
import ch.threema.app.webclient.converter.MsgpackArrayBuilder
import ch.threema.app.webclient.converter.Receiver
import ch.threema.app.webclient.exceptions.ConversionException
import ch.threema.app.webclient.services.instance.MessageDispatcher
import ch.threema.app.webclient.services.instance.MessageUpdater
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.storage.models.AbstractMessageModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.msgpack.core.MessagePackException

private val logger = getThreemaLogger("MessageUpdateHandler")

@WorkerThread
class MessageUpdateHandler @AnyThread constructor(
    private val handler: HandlerExecutor,
    private val dispatcher: MessageDispatcher,
    private val conversationCategoryService: ConversationCategoryService,
    private val fileService: FileService,
) : MessageUpdater(Protocol.SUB_TYPE_MESSAGES), KoinComponent {
    private val globalEventFlows: GlobalEventFlows by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        Protocol.ARGUMENT_MODE_NEW,
        Protocol.ARGUMENT_MODE_MODIFIED,
        Protocol.ARGUMENT_MODE_REMOVED,
    )
    private annotation class Mode

    private val receivers: MutableSet<MessageReceiver<*>> = mutableSetOf()

    // Ring buffer with 64 entries to keep track of messages where the
    // thumbnail has already been sent. This is done to send the thumbnail
    // only once, to reduce the network traffic.
    // Implementation note: https://stackoverflow.com/a/1963881/284318
    private val sentThumbnails: HashMap<Int, Boolean> = object : LinkedHashMap<Int, Boolean>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Boolean>) = size > 64
    }

    private var coroutineScope: CoroutineScope? = null

    override fun register() {
        coroutineScope?.cancel()
        coroutineScope = CoroutineScope(dispatcherProvider.worker)

        coroutineScope?.launch {
            globalEventFlows.messages.collect { event ->
                when (event) {
                    is MessageEvent.NewMessage -> onNewMessage(event.message)
                    is MessageEvent.MessagesUpdated -> onMessagesModified(event.messages)
                    is MessageEvent.MessageRemovedLocally -> onMessageRemoved(event.message)
                    is MessageEvent.MessageEdited,
                    is MessageEvent.MessageDeletedForAll,
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

    fun register(receiver: MessageReceiver<*>): Boolean {
        val added = receivers.add(receiver)
        register()
        return added
    }

    fun unregister(receiver: MessageReceiver<*>): Boolean {
        val removed = receivers.remove(receiver)
        if (receivers.isEmpty()) {
            unregister()
        }
        return removed
    }

    private fun sendThumbnail(message: AbstractMessageModel): Boolean {
        if (!MessageUtil.canHaveThumbnailFile(message)) {
            // This message type cannot possibly have a thumbnail
            return false
        }
        if (message.uid?.let { messageUid -> fileService.hasMessageThumbnail(messageUid) } != true) {
            // No thumbnail file exists so far
            return false
        }
        if (sentThumbnails.containsKey(message.id)) {
            // Thumbnail for this message was already sent
            return false
        }
        // Thumbnail exists but wasn't sent yet!
        sentThumbnails[message.id] = true
        return true
    }

    private fun onNewMessage(newMessage: AbstractMessageModel) {
        dispatch(listOf(newMessage), Protocol.ARGUMENT_MODE_NEW)
    }

    private fun onMessagesModified(modifiedMessageModels: List<AbstractMessageModel>) {
        // split deleted messages to dispatch with different mode
        modifiedMessageModels
            .groupBy { message ->
                if (message.isDeleted) Protocol.ARGUMENT_MODE_REMOVED else Protocol.ARGUMENT_MODE_MODIFIED
            }
            .forEach { (mode: String, modifiedMessages: List<AbstractMessageModel>) ->
                dispatch(modifiedMessages, mode)
            }
    }

    private fun onMessageRemoved(removedMessageModel: AbstractMessageModel) {
        dispatch(listOf(removedMessageModel), Protocol.ARGUMENT_MODE_REMOVED)
    }

    private fun dispatch(messages: List<AbstractMessageModel>, @Mode mode: String) {
        handler.post {
            // Group messages by receiver
            val outbox = mutableMapOf<MessageReceiver<*>, MutableList<AbstractMessageModel>>()
            for (message in messages) {
                val receiver = receivers.find { receiver ->
                    receiver.isMessageBelongsToMe(message)
                }
                // Skip chat messages in private chats (#WEBC-75)
                if (receiver != null && !conversationCategoryService.isMarkedAsPrivate(conversationId = receiver.conversationId)) {
                    outbox.getOrPut(receiver) { mutableListOf() }.add(message)
                }
            }
            update(outbox, mode)
        }
    }

    private fun update(outbox: Map<MessageReceiver<*>, List<AbstractMessageModel>>, @Mode mode: String) {
        outbox.forEach { (receiver, messages) ->
            try {
                val arguments = Receiver.getArguments(receiver).put(Protocol.ARGUMENT_MODE, mode)

                val data = MsgpackArrayBuilder()
                for (message in messages) {
                    data.put(
                        Message.convert(
                            message,
                            receiver,
                            sendThumbnail(message),
                            if (Protocol.ARGUMENT_MODE_REMOVED == mode) Message.DETAILS_MINIMAL else Message.DETAILS_FULL,
                        ),
                    )
                }

                logger.debug("Sending messages update")
                send(dispatcher, data, arguments)
            } catch (e: ConversionException) {
                logger.error("Exception", e)
            } catch (e: MessagePackException) {
                logger.error("Exception", e)
            }
        }
    }
}
