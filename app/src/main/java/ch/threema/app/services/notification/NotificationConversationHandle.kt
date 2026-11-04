package ch.threema.app.services.notification

import android.graphics.Bitmap
import ch.threema.android.ResolvableString
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import java.time.Instant

class NotificationConversationHandle(
    @JvmField val conversationId: ConversationId,
    @JvmField var name: ResolvableString,
    @JvmField var notificationTriggerPolicyOverride: NotificationTriggerPolicyOverride<*>?,
    private val onFetchAvatar: () -> Bitmap?,
) {

    @JvmField
    var lastNotificationTime: Instant = Instant.EPOCH

    val notificationId: Int = NotificationIDs.getNotificationIdForConversation(conversationId)

    val notificationIdEmojiReaction: Int = NotificationIDs.getNotificationIdForConversation(
        conversationId = conversationId,
        offset = NotificationIDs.Offset.EMOJI_REACTION_NOTIFICATION,
    )

    fun getRequestCode(action: NotificationRequestCodes.ConversationNotificationAction) =
        NotificationRequestCodes.getRequestCodeForConversationNotification(conversationId, action)

    fun loadAvatar(): Bitmap? = onFetchAvatar()
}
