package ch.threema.app.usecases.conversations

import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.services.ConversationService
import ch.threema.common.DispatcherProvider
import ch.threema.storage.models.ConversationModel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform

class WatchUnarchivedConversationsUseCase(
    private val conversationService: ConversationService,
    private val globalEventFlows: GlobalEventFlows,
    private val dispatcherProvider: DispatcherProvider,
) : WatchConversationsUseCase {

    /**
     *  Creates a *cold* [Flow] of the latest non-archived conversation models.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current list of all non-archived conversation models.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, any unconsumed values are **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception will flow downstream.
     */
    override fun call(): Flow<List<ConversationModel>> =
        globalEventFlows.conversations.transform { event ->
            when (event) {
                ConversationEvent.AllConversationsUpdated,
                is ConversationEvent.ConversationUpdated,
                is ConversationEvent.ConversationRemoved,
                is ConversationEvent.NewConversation,
                -> emit(getCurrentConversations())
                is ConversationEvent.ConversationArchived,
                is ConversationEvent.ConversationDeleted,
                -> Unit
            }
        }
            .onStart {
                emit(getCurrentConversations())
            }
            .buffer(capacity = CONFLATED)
            .flowOn(dispatcherProvider.io)

    private fun getCurrentConversations(): List<ConversationModel> =
        conversationService.getAll(false)
}
