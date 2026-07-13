package ch.threema.app.services.notification

import android.graphics.Bitmap
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.data.datatypes.ConversationId
import java.time.Instant

data class ConversationNotificationGroup(
    @JvmField val uid: String,
    @JvmField var name: String,
    @JvmField var shortName: String?,
    @JvmField val messageReceiver: MessageReceiver<*>,
    private val onFetchAvatar: () -> Bitmap?,
) {

    @JvmField
    val conversationId: ConversationId = messageReceiver.conversationId

    @JvmField
    var lastNotificationDate: Instant = Instant.EPOCH

    @JvmField
    val conversations: MutableList<ConversationNotification> = mutableListOf()

    @JvmField
    val notificationId: Int = NotificationIDs.getNotificationIdForConversation(conversationId)

    fun getRequestCode(action: NotificationRequestCodes.ConversationNotificationAction) =
        NotificationRequestCodes.getRequestCodeForConversationNotification(conversationId, action)

    fun loadAvatar(): Bitmap? = onFetchAvatar()
}
