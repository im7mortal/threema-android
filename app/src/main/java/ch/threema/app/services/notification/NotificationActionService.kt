package ch.threema.app.services.notification

import android.app.IntentService
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.core.app.RemoteInput
import androidx.core.content.getSystemService
import ch.threema.android.ToastDuration
import ch.threema.android.buildServiceIntent
import ch.threema.android.showToast
import ch.threema.app.AppConstants
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.di.awaitSessionScopeReady
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.emojis.EmojiUtil
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.MessageService
import ch.threema.app.services.withConnection
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.takeUnlessBlank
import ch.threema.common.waitAtMost
import ch.threema.data.datatypes.ConversationId
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.getConversationId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("NotificationActionService")

class NotificationActionService : IntentService(TAG), KoinComponent {
    private val messageService: MessageService? by injectNullableNonBinding()
    private val lifetimeService: LifetimeService? by injectNullableNonBinding()
    private val notificationService: NotificationService? by injectNullableNonBinding()

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        runBlocking {
            waitAtMost(10.seconds) {
                awaitSessionScopeReady()
            }
        }
        val messageReceiver = intent?.let { IntentDataUtil.getMessageReceiverFromIntent(this, intent) }
        if (messageReceiver == null) {
            showToast(R.string.verify_failed)
            logger.error("Failed to handle notification action - no message receiver")
            return
        }

        val messageModel = IntentDataUtil.getMessageModelFromReceiver(intent, messageReceiver)
        when (val action = intent.action) {
            ACTION_REPLY -> if (reply(messageReceiver, intent)) {
                return
            }
            ACTION_MARK_AS_READ -> {
                markAsRead(messageReceiver)
                return
            }
            ACTION_ACK -> if (messageModel != null) {
                ack(messageReceiver, messageModel)
                return
            }
            ACTION_DEC -> if (messageModel != null) {
                dec(messageReceiver, messageModel)
                return
            }
            else -> logger.info("Unknown action {}", action)
        }

        showToast(R.string.verify_failed)
        logger.error("Failed to handle notification action")
    }

    @WorkerThread
    private fun ack(messageReceiver: MessageReceiver<*>, messageModel: MessageModel) {
        sendEmojiReaction(
            messageReceiver = messageReceiver,
            messageModel = messageModel,
            emojiSequence = EmojiUtil.THUMBS_UP_SEQUENCE,
            toastMessage = R.string.message_acknowledged,
        )
    }

    @WorkerThread
    private fun dec(messageReceiver: MessageReceiver<*>, messageModel: MessageModel) {
        sendEmojiReaction(
            messageReceiver = messageReceiver,
            messageModel = messageModel,
            emojiSequence = EmojiUtil.THUMBS_DOWN_SEQUENCE,
            toastMessage = R.string.message_declined,
        )
    }

    private fun sendEmojiReaction(
        messageReceiver: MessageReceiver<*>,
        messageModel: MessageModel,
        emojiSequence: String,
        @StringRes toastMessage: Int,
    ) {
        val messageService = messageService
        val lifetimeService = lifetimeService
        val notificationService = notificationService
        if (messageService == null || lifetimeService == null || notificationService == null) {
            logger.error("Failed to send emoji reaction, services unavailable")
            return
        }

        lifetimeService.withConnection(TAG, linger = NOTIFICATION_ACTION_CONNECTION_LINGER) {
            try {
                messageService.markConversationAsRead(messageReceiver, notificationService)
                messageService.sendEmojiReaction(
                    messageModel,
                    emojiSequence,
                    messageReceiver,
                    false,
                )
                notificationService.cancelAllConversationNotifications(messageModel.getConversationId())
                showToast(toastMessage)
            } catch (e: Exception) {
                logger.error("Failed to send emoji reaction", e)
            }
        }
    }

    private fun reply(messageReceiver: MessageReceiver<*>, intent: Intent): Boolean {
        val messageService = messageService
        val lifetimeService = lifetimeService
        val notificationService = notificationService
        if (messageService == null || lifetimeService == null || notificationService == null) {
            logger.error("Failed to reply, services unavailable")
            return false
        }

        val message = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AppConstants.EXTRA_VOICE_REPLY)
            ?.toString()
            ?.takeUnlessBlank()
            ?: return false

        lifetimeService.withConnection(TAG, linger = NOTIFICATION_ACTION_CONNECTION_LINGER) {
            try {
                messageService.sendText(message, messageReceiver)
                messageService.markConversationAsRead(messageReceiver, notificationService)
                notificationService.cancelAllConversationNotifications(messageReceiver.getConversationId())

                showToast(R.string.message_sent)
                return true
            } catch (e: Exception) {
                logger.error("Failed to send message", e)
            }
        }
        return false
    }

    private fun markAsRead(messageReceiver: MessageReceiver<*>) {
        val messageService = messageService
        val lifetimeService = lifetimeService
        val notificationService = notificationService
        if (messageService == null || lifetimeService == null || notificationService == null) {
            logger.error("Failed to mark as read, services unavailable")
            return
        }

        lifetimeService.withConnection(TAG, linger = NOTIFICATION_ACTION_CONNECTION_LINGER) {
            messageService.markConversationAsRead(messageReceiver, notificationService)
        }
        notificationService.cancelAllConversationNotifications(messageReceiver.getConversationId())
    }

    private fun showToast(@StringRes stringRes: Int) {
        val uiModeManager = getSystemService<UiModeManager>()
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR) {
            logger.info("Toast suppressed due to car connection: {}", getString(stringRes))
        } else {
            showToast(stringRes, ToastDuration.LONG)
        }
    }

    companion object {
        private const val ACTION_REPLY = BuildConfig.APPLICATION_ID + ".REPLY"
        private const val ACTION_MARK_AS_READ = BuildConfig.APPLICATION_ID + ".MARK_AS_READ"
        private const val ACTION_ACK = BuildConfig.APPLICATION_ID + ".ACK"
        private const val ACTION_DEC = BuildConfig.APPLICATION_ID + ".DEC"

        fun createReplyIntent(context: Context, conversationId: ConversationId): Intent =
            buildServiceIntent<NotificationActionService>(context) {
                action = ACTION_REPLY
                IntentDataUtil.addConversationIdToIntent(this, conversationId)
            }

        fun createMarkAsReadIntent(context: Context, conversationId: ConversationId): Intent =
            buildServiceIntent<NotificationActionService>(context) {
                action = ACTION_MARK_AS_READ
                IntentDataUtil.addConversationIdToIntent(this, conversationId)
            }

        fun createAckIntent(context: Context, conversationId: ConversationId, messageId: Int): Intent =
            buildServiceIntent<NotificationActionService>(context) {
                action = ACTION_ACK
                IntentDataUtil.addConversationIdToIntent(this, conversationId)
                putExtra(AppConstants.INTENT_DATA_MESSAGE_ID, messageId)
            }

        fun createDecIntent(context: Context, conversationId: ConversationId, messageId: Int): Intent =
            buildServiceIntent<NotificationActionService>(context) {
                action = ACTION_DEC
                IntentDataUtil.addConversationIdToIntent(this, conversationId)
                putExtra(AppConstants.INTENT_DATA_MESSAGE_ID, messageId)
            }

        private const val TAG = "notificationAction"
        private val NOTIFICATION_ACTION_CONNECTION_LINGER = 5.seconds
    }
}
