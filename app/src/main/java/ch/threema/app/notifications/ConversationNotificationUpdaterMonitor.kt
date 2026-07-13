package ch.threema.app.notifications

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.monitors.Monitor
import ch.threema.app.services.ConversationService
import ch.threema.app.services.GroupService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.notification.NotificationService
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.localGroupId
import ch.threema.domain.protocol.csp.messages.file.FileData.RENDERING_MEDIA
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class ConversationNotificationUpdaterMonitor(
    private val globalEventFlows: GlobalEventFlows,
    private val conversationNotificationConverter: ConversationNotificationConverter,
    private val identityProvider: IdentityProvider,
    private val lockAppService: LockAppService,
) : Monitor("ConversationNotificationUpdaterMonitor"), KoinComponent {
    private val notificationService: NotificationService? by injectNullableNonBinding()
    private val conversationService: ConversationService? by injectNullableNonBinding()
    private val groupService: GroupService? by injectNullableNonBinding()

    override suspend fun run(): Unit = coroutineScope {
        launch {
            globalEventFlows.conversations.collect { event ->
                when (event) {
                    is ConversationEvent.ConversationArchived -> cancelNotification(event.conversation.id)
                    is ConversationEvent.ConversationDeleted -> cancelNotification(event.conversation.id)
                    is ConversationEvent.NewConversation,
                    is ConversationEvent.ConversationUpdated,
                    is ConversationEvent.ConversationRemoved,
                    ConversationEvent.AllConversationsUpdated,
                    -> Unit
                }
            }
        }
        launch {
            globalEventFlows.messages.collect { event ->
                when (event) {
                    is MessageEvent.NewMessage -> onNewMessage(event.message)
                    is MessageEvent.MessageDeletedForAll -> showConversationNotification(event.message, updateExisting = true)
                    is MessageEvent.MessageEdited -> showConversationNotification(event.message, updateExisting = true)
                    is MessageEvent.MessagesUpdated -> onModifiedMessages(event.messages)
                    is MessageEvent.MessageRemovedLocally,
                    -> Unit
                }
            }
        }
        launch {
            globalEventFlows.groups.collect { event ->
                when (event) {
                    is GroupEvent.MemberKicked -> if (event.identity == identityProvider.getIdentity()) {
                        removeGroupCallNotifications(event.groupIdentity)
                    }
                    is GroupEvent.UserLeftGroup -> removeGroupCallNotifications(event.groupIdentity)
                    is GroupEvent.GroupRemoved,
                    is GroupEvent.MemberLeft,
                    is GroupEvent.GroupProfilePictureUpdated,
                    is GroupEvent.GroupRenamed,
                    is GroupEvent.GroupStateChanged,
                    is GroupEvent.GroupUpdated,
                    is GroupEvent.NewGroup,
                    is GroupEvent.NewMember,
                    -> Unit
                }
            }
        }
        launch {
            watchAppLock()
        }
    }

    private fun cancelNotification(conversationId: ConversationId) {
        notificationService?.cancel(conversationId)
    }

    private fun onNewMessage(message: AbstractMessageModel) {
        if (message.isStatusMessage) {
            return
        }

        val conversation = conversationService?.get(message)
        if (conversation != null) {
            showConversationNotification(message, updateExisting = false)
        }
    }

    private fun onModifiedMessages(messages: List<AbstractMessageModel>) {
        messages.forEach { message ->
            if (message.isImageOrVideoMessage()) {
                showConversationNotification(message, updateExisting = true)
            }
        }
    }

    private fun AbstractMessageModel.isImageOrVideoMessage(): Boolean =
        type == MessageType.FILE &&
            fileData.renderingType == RENDERING_MEDIA &&
            (messageContentsType == MessageContentsType.IMAGE || messageContentsType == MessageContentsType.VIDEO)

    private fun showConversationNotification(message: AbstractMessageModel, updateExisting: Boolean) {
        if (message.isOutbox || message.isStatusMessage || message.isRead || message.type == MessageType.GROUP_CALL_STATUS) {
            return
        }

        notificationService?.showConversationNotification(
            conversationNotificationConverter.convert(message) ?: return,
            updateExisting,
        )
    }

    private fun removeGroupCallNotifications(groupIdentity: GroupIdentity) {
        val group = groupService?.getByGroupIdentity(groupIdentity) ?: return
        notificationService?.cancelGroupCallNotification(group.localGroupId)
    }

    private suspend fun watchAppLock() {
        lockAppService.watchLockState()
            .collect { state ->
                if (state == LockAppService.LockState.LOCKED) {
                    notificationService?.cancelConversationNotificationsOnLockApp()
                }
            }
    }
}
