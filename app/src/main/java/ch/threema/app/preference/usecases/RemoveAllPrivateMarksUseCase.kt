package ch.threema.app.preference.usecases

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.widget.WidgetUpdater
import ch.threema.data.datatypes.ConversationCategory

class RemoveAllPrivateMarksUseCase(
    private val conversationService: ConversationService,
    private val preferenceService: PreferenceService,
    private val distributionListService: DistributionListService,
    private val widgetUpdater: WidgetUpdater,
    private val globalEventBuses: GlobalEventBuses,
) {
    fun call() {
        val messageReceivers = conversationService.getAll(false)
            .plus(conversationService.archived)
            .map { conversation -> conversation.messageReceiver }
        messageReceivers.forEach { messageReceiver ->
            when (messageReceiver) {
                is ContactMessageReceiver -> messageReceiver.contactModel?.setConversationCategoryFromLocalOrRemote(ConversationCategory.DEFAULT)
                is GroupMessageReceiver -> messageReceiver.groupModel?.setConversationCategoryFromLocalOrRemote(ConversationCategory.DEFAULT)
                is DistributionListMessageReceiver -> distributionListService.setConversationCategory(
                    messageReceiver.distributionList,
                    ConversationCategory.DEFAULT,
                )
            }
        }
        preferenceService.setArePrivateChatsHidden(false)
        widgetUpdater.updateWidgets()
        globalEventBuses.conversations.emit(ConversationEvent.AllConversationsUpdated)
    }
}
