package ch.threema.app.usecases.groups

import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.usecases.conversations.AvatarIteration
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.repositories.GroupModelRepository
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Why this use-case exists: See [AvatarIteration] definition
 */
class WatchGroupAvatarIterationUseCase(
    private val globalEventFlows: GlobalEventFlows,
    private val groupModelRepository: GroupModelRepository,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     *  Creates a cold [Flow] of an incrementing [AvatarIteration] value for the given [groupConversationId].
     *
     *  ###### Direct emit promise
     *  This flow fulfills the promise to directly emit the current initial iteration value.
     *
     *  ###### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ###### Error strategy
     *  Every exception will flow downstream.
     */
    fun call(groupConversationId: GroupConversationId): Flow<AvatarIteration> {
        var avatarIteration = AvatarIteration.initial
        return flow {
            emit(avatarIteration)
            globalEventFlows
                .groups
                .filterIsInstance<GroupEvent.GroupProfilePictureUpdated>()
                .filter { event ->
                    withContext(dispatcherProvider.io) {
                        groupModelRepository.getByGroupIdentity(event.groupIdentity)?.getDatabaseId() == groupConversationId.groupDatabaseId
                    }
                }
                .collect {
                    avatarIteration = avatarIteration.inc()
                    emit(avatarIteration)
                }
        }
            .buffer(capacity = CONFLATED)
    }
}
