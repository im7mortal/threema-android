package ch.threema.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import ch.threema.android.buildNotification
import ch.threema.app.R
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ConversationId

private val logger = getThreemaLogger("ForwardSecurityNotificationManager")

class ForwardSecurityNotificationManager(
    private val context: Context,
    private val conversationCategoryService: ConversationCategoryService,
    private val preferenceService: PreferenceService,
) {
    @SuppressLint("MissingPermission")
    fun showForwardSecurityNotification(messageReceiver: MessageReceiver<*>) {
        val conversationId = messageReceiver.conversationId
        val contentText = getNotificationContextText(messageReceiver)

        val notification = buildNotification(context, NotificationChannels.NOTIFICATION_CHANNEL_FORWARD_SECURITY) {
            setContentTitle(context.getString(R.string.forward_security_notification_rejected_title))
            setContentText(contentText)
            setSmallIcon(R.drawable.ic_key_off)
            setLocalOnly(true)
            setContentIntent(getIntent(conversationId))
            setAutoCancel(true)
            setOnlyAlertOnce(true)
            setOngoing(false)
        }

        NotificationManagerCompat.from(context).apply {
            if (hasNotificationPermission()) {
                val notificationId = getNotificationId(conversationId)
                logger.info("Displaying fs reject notification with id {}", notificationId)
                notify(notificationId, notification)
            } else {
                logger.warn("Cannot show forward security notification due to missing permission")
            }
        }
    }

    private fun hasNotificationPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun getNotificationId(conversationId: ConversationId): Int =
        NotificationIDs.getNotificationIdForConversation(conversationId, offset = 1)

    private fun getIntent(conversationId: ConversationId): PendingIntent {
        val intent = ComposeMessageActivity.createIntent(
            context = context,
            conversationId = conversationId,
        )
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val requestCode = NotificationRequestCodes.getRequestCodeForConversationNotification(
            conversationId = conversationId,
            action = NotificationRequestCodes.ConversationNotificationAction.FORWARD_SECURITY,
        )
        return PendingIntent.getActivity(
            /* context = */
            context,
            /* requestCode = */
            requestCode,
            /* intent = */
            intent,
            /* flags = */
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getNotificationContextText(messageReceiver: MessageReceiver<*>): String {
        // Do not include name in case of a hidden contact. The intent remains the same, so clicking
        // the notification will still result in opening the correct chat.
        return if (conversationCategoryService.isMarkedAsPrivate(messageReceiver.conversationId)) {
            context.getString(R.string.forward_security_notification_rejected_text_generic)
        } else {
            when (messageReceiver) {
                is ContactMessageReceiver -> context.getString(
                    R.string.forward_security_notification_rejected_text_contact,
                    messageReceiver.getDisplayName(preferenceService.getContactNameFormat()),
                )

                is GroupMessageReceiver -> context.getString(
                    R.string.forward_security_notification_rejected_text_group,
                    messageReceiver.getDisplayName(preferenceService.getContactNameFormat()),
                )

                // Note that messages in distribution lists are rejected in the corresponding 1:1
                // chat and therefore handled via contact message receiver.
                else -> throw IllegalArgumentException("Cannot show notification for unexpected receiver type: " + messageReceiver.type)
            }
        }
    }
}
