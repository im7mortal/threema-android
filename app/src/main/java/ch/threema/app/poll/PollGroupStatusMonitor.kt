package ch.threema.app.poll

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.PollEvent
import ch.threema.app.monitors.Monitor
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.services.poll.PollService
import ch.threema.data.IdentityProvider
import ch.threema.domain.types.Identity
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType
import ch.threema.storage.models.poll.GroupPollModel
import ch.threema.storage.models.poll.PollModel
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.core.component.KoinComponent

class PollGroupStatusMonitor(
    private val globalEventFlows: GlobalEventFlows,
    private val identityProvider: IdentityProvider,
) : Monitor("PollGroupStatusMonitor"), KoinComponent {
    private val pollService: PollService? by injectNullableNonBinding()
    private val groupService: GroupService? by injectNullableNonBinding()
    private val messageService: MessageService? by injectNullableNonBinding()

    override suspend fun run() {
        globalEventFlows.polls
            .filterIsInstance<PollEvent.PollVoted>()
            .collect { event ->
                onPollVoted(event.poll, event.voterIdentity, event.isNewVote)
            }
    }

    private fun onPollVoted(pollModel: PollModel, voterIdentity: Identity, isNewVote: Boolean) {
        val linkModel = pollService?.getLinkedPollModel(pollModel) as? GroupPollModel ?: return
        val groupModel = groupService?.getById(linkModel.groupId) ?: return
        val receiver = groupService?.createReceiver(groupModel) ?: return

        val groupStatusType = when (pollModel.type) {
            PollModel.Type.RESULT_ON_CLOSE -> if (isNewVote) {
                GroupStatusType.RECEIVED_VOTE
            } else {
                null
            }
            PollModel.Type.INTERMEDIATE -> if (isNewVote) {
                GroupStatusType.FIRST_VOTE
            } else {
                GroupStatusType.MODIFIED_VOTE
            }
            else -> null
        }

        // Only show votes (and vote changes) to the creator of the poll in a group
        if (
            pollModel.creatorIdentity != identityProvider.getIdentityString() &&
            (groupStatusType == GroupStatusType.FIRST_VOTE || groupStatusType == GroupStatusType.MODIFIED_VOTE)
        ) {
            return
        }

        if (groupStatusType != null) {
            messageService?.createGroupStatus(
                receiver,
                groupStatusType,
                voterIdentity.value,
                pollModel.name,
                null,
            )
        }

        if (isNewVote && pollService?.getPendingParticipants(pollModel.id)?.isEmpty() == true) {
            messageService?.createGroupStatus(
                receiver,
                GroupStatusType.VOTES_COMPLETE,
                null,
                pollModel.name,
                null,
            )
        }
    }
}
