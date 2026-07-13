package ch.threema.app.home.usecases

import ch.threema.app.services.ConversationService
import ch.threema.app.services.ConversationTagService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationId
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.ConversationTag
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("GetUnreadConversationCountUseCase")

class GetUnreadConversationCountUseCase(
    private val conversationService: ConversationService,
    private val conversationTagService: ConversationTagService,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call() = withContext(dispatcherProvider.worker) {
        val conversationModels = conversationService.getAll(
            false,
            object : ConversationService.Filter {
                override fun onlyUnread() = true
            },
        )

        var unread = conversationModels.sumOf { conversationModel ->
            conversationModel.unreadCount.toInt()
        }

        // First check whether there are some conversations that are marked as unread. This
        // check is expected to be fast, as usually there are not many chats that are marked
        // as unread.
        if (conversationTagService.getCount(ConversationTag.MARKED_AS_UNREAD) > 0) {
            // In case there is at least one unread tag, we create a set of all possible
            // conversation ids to efficiently check that the unread tags are valid.
            val shownConversationIds = conversationService
                .getAll(false)
                .map(ConversationModel::id)
                .toSet()

            val unreadIds: List<ConversationId> = conversationTagService.getConversationIdsByTag(ConversationTag.MARKED_AS_UNREAD)
            for (unreadId in unreadIds) {
                if (unreadId in shownConversationIds) {
                    unread++
                } else {
                    logger.warn("Conversation '{}' is marked as unread but not shown. Deleting the unread flag.", unreadId)
                    conversationTagService.removeTag(
                        conversationId = unreadId,
                        tag = ConversationTag.MARKED_AS_UNREAD,
                        triggerSource = TriggerSource.LOCAL,
                    )
                }
            }
        }

        unread
    }
}
