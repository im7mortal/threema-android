package ch.threema.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import ch.threema.android.ToastDuration
import ch.threema.android.buildBroadcastReceiverIntent
import ch.threema.android.goAsync
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.services.ContactService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.MessageService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.services.withConnection
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.group.GroupMessageModel
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReSendMessagesBroadcastReceiver")

class ReSendMessagesBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val messageService: MessageService by inject()
    private val lifetimeService: LifetimeService by inject()
    private val contactService: ContactService by inject()
    private val groupService: GroupService by inject()
    private val distributionListService: DistributionListService by inject()
    private val notificationService: NotificationService by inject()

    override fun onReceive(context: Context, intent: Intent?) = goAsync {
        // We can't wait for long, as the broadcast receiver needs to complete within a few seconds
        awaitAppFullyReadyWithTimeout(timeout = 5.seconds)
            ?: run {
                logger.error("App did not become ready in time for message resending")
                return@goAsync
            }

        cancelFailedMessagesNotification(context)

        val failedMessages = IntentDataUtil.getAbstractMessageModels(intent, messageService)
        if (failedMessages.isEmpty()) {
            return@goAsync
        }

        // we need to make sure there's a connection during delivery
        lifetimeService.withConnection(TAG, linger = CONNECTION_LINGER) {
            processFailedMessages(context, failedMessages)
        }
    }

    private fun cancelFailedMessagesNotification(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(NotificationIDs.UNSENT_MESSAGE_NOTIFICATION_ID)
    }

    private fun processFailedMessages(context: Context, failedMessages: List<AbstractMessageModel>) {
        for (failedMessage in failedMessages) {
            val messageReceiver = getMessageReceiverFromMessageModel(failedMessage)
            if (messageReceiver == null) {
                logger.warn("Message receiver is null for failed message {}", failedMessage.apiMessageId)
                continue
            }

            val receiverIdentities = failedMessage.getReceiverIdentities()
            if (receiverIdentities.isEmpty()) {
                continue
            }

            try {
                resendMessage(failedMessage, messageReceiver, receiverIdentities)
                notificationService.cancel(messageReceiver.conversationId)
            } catch (e: Exception) {
                context.showToast(R.string.original_file_no_longer_avilable, ToastDuration.LONG)
                logger.error("Failed to resend message", e)
            }
        }
    }

    @WorkerThread
    private fun getMessageReceiverFromMessageModel(messageModel: AbstractMessageModel?): MessageReceiver<out AbstractMessageModel>? =
        when (messageModel) {
            is MessageModel -> {
                contactService.createReceiver(contactService.getByIdentity(messageModel.identity))
            }
            is GroupMessageModel -> {
                groupService.createReceiver(groupService.getById(messageModel.groupId)!!)
            }
            is DistributionListMessageModel -> {
                distributionListService.createReceiver(distributionListService.getById(messageModel.distributionListId)!!)
            }
            else -> null
        }

    @WorkerThread
    private fun AbstractMessageModel.getReceiverIdentities() = buildList<IdentityString> {
        if (this is GroupMessageModel) {
            val group = groupService.getById(groupId)
            if (group == null) {
                logger.warn("Group model not found for failed message {}", apiMessageId)
            } else {
                addAll(groupService.getGroupMemberIdentities(group))
            }
        } else {
            identity?.let { add(it) }
        }
    }

    @WorkerThread
    private fun resendMessage(
        message: AbstractMessageModel,
        messageReceiver: MessageReceiver<out AbstractMessageModel>,
        receiverIdentities: List<IdentityString>,
    ) {
        messageService.resendMessage(
            message,
            messageReceiver as MessageReceiver<AbstractMessageModel>,
            null,
            receiverIdentities,
            MessageId.random(),
            TriggerSource.LOCAL,
        )
    }

    companion object {
        fun createIntent(
            context: Context,
            failedMessages: List<AbstractMessageModel>,
        ) = buildBroadcastReceiverIntent<ReSendMessagesBroadcastReceiver>(context) {
            IntentDataUtil.appendMultipleMessageTypes(failedMessages, this)
        }

        private const val TAG = "ReSendMessagesBroadcastReceiver"
        private val CONNECTION_LINGER = 5.seconds
    }
}
