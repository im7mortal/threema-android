package ch.threema.app.conversation

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.DistributionListEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.monitors.Monitor
import ch.threema.app.services.ConversationService
import ch.threema.app.services.GroupService
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class ConversationRefreshMonitor(
    private val globalEventFlows: GlobalEventFlows,
    private val identityProvider: IdentityProvider,
) : Monitor("MessageRefreshMonitor"), KoinComponent {
    private val contactModelRepository: ContactModelRepository? by injectNullableNonBinding()
    private val conversationService: ConversationService? by injectNullableNonBinding()
    private val groupService: GroupService? by injectNullableNonBinding()

    override suspend fun run(): Unit = coroutineScope {
        launch {
            globalEventFlows.messages.collect { event ->
                when (event) {
                    is MessageEvent.NewMessage -> onNewMessage(event.message)
                    is MessageEvent.MessagesUpdated -> onMessagesUpdated(event.messages)
                    is MessageEvent.MessageRemovedLocally -> onMessageRemoved(event.message)
                    is MessageEvent.MessageEdited,
                    is MessageEvent.MessageDeletedForAll,
                    -> Unit
                }
            }
        }
        launch {
            globalEventFlows.contacts
                .filterIsInstance<ContactEvent.ContactUpdated>()
                .collect { event ->
                    refreshContactConversation(event.identity)
                }
        }
        launch {
            globalEventFlows.groups.collect { event ->
                when (event) {
                    is GroupEvent.GroupRenamed -> refreshGroupConversation(event.groupIdentity)
                    is GroupEvent.GroupUpdated -> refreshGroupConversation(event.groupIdentity)
                    is GroupEvent.MemberKicked -> if (event.identity == identityProvider.getIdentity()) {
                        refreshGroupConversation(event.groupIdentity)
                    }
                    is GroupEvent.NewMember -> refreshGroupConversation(event.groupIdentity)
                    is GroupEvent.UserLeftGroup -> refreshGroupConversation(event.groupIdentity)
                    is GroupEvent.GroupProfilePictureUpdated,
                    is GroupEvent.GroupRemoved,
                    is GroupEvent.GroupStateChanged,
                    is GroupEvent.MemberLeft,
                    is GroupEvent.NewGroup,
                    -> Unit
                }
            }
        }
        launch {
            globalEventFlows.distributionLists.collect { event ->
                when (event) {
                    is DistributionListEvent.NewDistributionList -> {
                        conversationService?.refresh(event.distributionList)
                    }
                    is DistributionListEvent.DistributionListUpdated -> {
                        conversationService?.refresh(event.distributionList)
                    }
                    is DistributionListEvent.DistributionListRemoved -> Unit
                }
            }
        }
    }

    private fun onNewMessage(message: AbstractMessageModel) {
        val conversationService = conversationService ?: return
        if (!message.isStatusMessage || message.type == MessageType.GROUP_CALL_STATUS) {
            conversationService.refreshForMessage(message, true)
        }
    }

    private fun onMessagesUpdated(messages: List<AbstractMessageModel>) {
        val conversationService = conversationService ?: return
        messages.forEach { message ->
            if (!message.isStatusMessage) {
                conversationService.refreshForMessage(message, false)
            }
        }
    }

    private fun onMessageRemoved(message: AbstractMessageModel) {
        val conversationService = conversationService ?: return
        if (!message.isStatusMessage) {
            conversationService.refreshWithDeletedMessage(message)
        }
    }

    private fun refreshContactConversation(identity: Identity) {
        conversationService?.updateContactConversation(identity.value)
        contactModelRepository?.getByIdentity(identity)
            ?.let { contactModel ->
                conversationService?.refresh(contactModel)
            }
    }

    private fun refreshGroupConversation(groupIdentity: GroupIdentity) {
        groupService?.removeFromCache(groupIdentity)
        groupService?.getByGroupIdentity(groupIdentity)
            ?.let { group ->
                conversationService?.refresh(group)
            }
    }
}
