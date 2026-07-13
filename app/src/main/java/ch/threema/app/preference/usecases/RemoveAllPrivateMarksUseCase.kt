package ch.threema.app.preference.usecases

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.eventbus.events.DistributionListEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.widget.WidgetUpdater
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.domain.types.Identity

class RemoveAllPrivateMarksUseCase(
    private val conversationService: ConversationService,
    private val conversationCategoryService: ConversationCategoryService,
    private val preferenceService: PreferenceService,
    private val widgetUpdater: WidgetUpdater,
    private val globalEventBuses: GlobalEventBuses,
) {
    fun call() {
        val messageReceivers = conversationService.getAll(false)
            .plus(conversationService.archived)
            .map { conversation -> conversation.messageReceiver }
        var hadPrivateConversations = false
        messageReceivers.forEach { messageReceiver ->
            if (conversationCategoryService.removePrivateMark(messageReceiver.conversationId)) {
                fireReceiverUpdate(messageReceiver)
                hadPrivateConversations = true
            }
        }
        if (hadPrivateConversations) {
            preferenceService.setArePrivateChatsHidden(false)
            widgetUpdater.updateWidgets()
            globalEventBuses.conversations.emit(ConversationEvent.AllConversationsUpdated)
        }
    }

    private fun fireReceiverUpdate(receiver: MessageReceiver<*>) {
        when (receiver) {
            is ContactMessageReceiver -> {
                globalEventBuses.contacts.emit(ContactEvent.ContactUpdated(Identity(receiver.contact.identity)))
            }
            is GroupMessageReceiver -> {
                val groupIdentity = GroupIdentity(
                    creatorIdentity = receiver.group.creatorIdentity,
                    groupId = receiver.group.apiGroupId.toLong(),
                )
                globalEventBuses.groups.emit(GroupEvent.GroupUpdated(groupIdentity))
            }
            is DistributionListMessageReceiver -> {
                globalEventBuses.distributionLists.emit(
                    DistributionListEvent.DistributionListUpdated(receiver.distributionList),
                )
            }
        }
    }
}
