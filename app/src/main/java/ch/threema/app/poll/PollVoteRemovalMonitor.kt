package ch.threema.app.poll

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.monitors.Monitor
import ch.threema.app.services.GroupService
import ch.threema.app.services.poll.PollService
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.domain.types.Identity
import org.koin.core.component.KoinComponent

class PollVoteRemovalMonitor(
    private val globalEventFlows: GlobalEventFlows,
) : Monitor("PollVoteRemovalMonitor"), KoinComponent {
    private val groupService: GroupService? by injectNullableNonBinding()
    private val pollService: PollService? by injectNullableNonBinding()

    override suspend fun run() {
        globalEventFlows.groups.collect { event ->
            when (event) {
                is GroupEvent.MemberKicked -> removeVotes(event.groupIdentity, event.identity)
                is GroupEvent.MemberLeft -> removeVotes(event.groupIdentity, event.identity)
                is GroupEvent.GroupProfilePictureUpdated,
                is GroupEvent.GroupRemoved,
                is GroupEvent.GroupRenamed,
                is GroupEvent.GroupStateChanged,
                is GroupEvent.GroupUpdated,
                is GroupEvent.NewGroup,
                is GroupEvent.NewMember,
                is GroupEvent.UserLeftGroup,
                -> Unit
            }
        }
    }

    private fun removeVotes(groupIdentity: GroupIdentity, identity: Identity) {
        val receiver = groupService?.createReceiver(groupIdentity) ?: return
        pollService?.removeVotes(receiver, identity.value)
    }
}
