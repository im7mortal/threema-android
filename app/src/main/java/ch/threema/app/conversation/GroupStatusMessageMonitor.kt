package ch.threema.app.conversation

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.monitors.Monitor
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupState
import ch.threema.domain.types.Identity
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class GroupStatusMessageMonitor(
    private val globalEventFlows: GlobalEventFlows,
    private val dispatcherProvider: DispatcherProvider,
) : Monitor("GroupStatusMessageMonitor"), KoinComponent {
    private val groupService: GroupService? by injectNullableNonBinding()
    private val messageService: MessageService? by injectNullableNonBinding()

    private val recentlyCreatedGroupIdentities = ConcurrentHashMap<GroupIdentity, Unit>()

    override suspend fun run() = coroutineScope {
        globalEventFlows.groups.collect { event ->
            when (event) {
                is GroupEvent.NewGroup -> {
                    createGroupStatus(event.groupIdentity, type = GroupStatusType.CREATED)
                    recentlyCreatedGroupIdentities[event.groupIdentity] = Unit
                    launch {
                        delay(GROUP_CREATION_TIMEOUT)
                        recentlyCreatedGroupIdentities.remove(event.groupIdentity)
                    }
                }
                is GroupEvent.NewMember -> if (!shouldIgnore(event.groupIdentity)) {
                    createGroupStatus(event.groupIdentity, type = GroupStatusType.MEMBER_ADDED, identity = event.identity)
                }
                is GroupEvent.GroupProfilePictureUpdated -> if (!shouldIgnore(event.groupIdentity)) {
                    createGroupStatus(event.groupIdentity, type = GroupStatusType.PROFILE_PICTURE_UPDATED)
                }
                is GroupEvent.GroupRenamed -> if (!shouldIgnore(event.groupIdentity)) {
                    createGroupStatus(event.groupIdentity, type = GroupStatusType.RENAMED, newGroupName = event.newName)
                }
                is GroupEvent.MemberKicked -> createGroupStatus(event.groupIdentity, type = GroupStatusType.MEMBER_KICKED, identity = event.identity)
                is GroupEvent.MemberLeft -> createGroupStatus(event.groupIdentity, type = GroupStatusType.MEMBER_LEFT, identity = event.identity)
                is GroupEvent.GroupStateChanged -> onGroupStateChanged(event.groupIdentity, event.newState)
                is GroupEvent.GroupRemoved,
                is GroupEvent.GroupUpdated,
                is GroupEvent.UserLeftGroup,
                -> Unit
            }
        }
    }

    private fun shouldIgnore(groupIdentity: GroupIdentity): Boolean =
        recentlyCreatedGroupIdentities.containsKey(groupIdentity)

    private suspend fun onGroupStateChanged(groupIdentity: GroupIdentity, newState: GroupState) {
        createGroupStatus(
            groupIdentity,
            type = when (newState) {
                GroupState.NOTES -> GroupStatusType.IS_NOTES_GROUP
                GroupState.PEOPLE -> GroupStatusType.IS_PEOPLE_GROUP
            },
        )
    }

    private suspend fun createGroupStatus(
        groupIdentity: GroupIdentity,
        type: GroupStatusType,
        identity: Identity? = null,
        newGroupName: String? = null,
    ) = withContext(dispatcherProvider.io) {
        val receiver = groupService?.createReceiver(groupIdentity) ?: return@withContext
        messageService?.createGroupStatus(
            receiver,
            type,
            identity?.value,
            null,
            newGroupName,
        )
    }

    companion object {
        /**
         * Certain events that occur within this time after the creation of a group are considered to be part of the group creation itself
         * instead of representing subsequent changes to the group. These events will not result in the creation of group status messages.
         */
        private val GROUP_CREATION_TIMEOUT = 5.seconds
    }
}
