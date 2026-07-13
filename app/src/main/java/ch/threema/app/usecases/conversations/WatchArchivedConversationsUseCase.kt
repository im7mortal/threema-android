package ch.threema.app.usecases.conversations

import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.services.ConversationService
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.storage.models.ConversationModel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform

class WatchArchivedConversationsUseCase(
    private val conversationService: ConversationService,
    private val globalEventFlows: GlobalEventFlows,
    private val dispatcherProvider: DispatcherProvider,
) : WatchConversationsUseCase {

    /**
     *  Creates a *cold* [Flow] of the most recent archived conversation models.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current list of all conversation models.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, any old unconsumed values are **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception will flow downstream.
     *
     *  ##### Event bus logic
     *  - This event bus will also get any events from un-archived conversations
     *  - We skip unnecessary re-reads of the archived conversations from database and only emit a new list of archived conversations
     *  if the change(s) affected an archived conversation
     *  - In the event of [ConversationEvent.AllConversationsUpdated] all archived conversations must be read from database in every case
     */
    override fun call(): Flow<List<ConversationModel>> =
        globalEventFlows.conversations.transform { event ->
            when (event) {
                is ConversationEvent.NewConversation,
                ConversationEvent.AllConversationsUpdated,
                -> emit(getCurrentArchivedConversations())
                is ConversationEvent.ConversationUpdated -> if (event.conversation.conversationVisibility == ConversationVisibility.ARCHIVED) {
                    emit(getCurrentArchivedConversations())
                }
                is ConversationEvent.ConversationRemoved -> if (event.conversation.conversationVisibility == ConversationVisibility.ARCHIVED) {
                    emit(getCurrentArchivedConversations())
                }
                is ConversationEvent.ConversationArchived,
                is ConversationEvent.ConversationDeleted,
                -> Unit
            }
        }
            .onStart {
                emit(getCurrentArchivedConversations())
            }
            .buffer(capacity = CONFLATED)
            .flowOn(context = dispatcherProvider.io)

    private fun getCurrentArchivedConversations(): List<ConversationModel> =
        conversationService.getArchived()
}
