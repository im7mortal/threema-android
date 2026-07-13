package ch.threema.app.notifications

import ch.threema.data.datatypes.ConversationId

/**
 * Manages all the request codes used for pending intents in notifications, to ensure that request codes are distinct.
 *
 * @see NotificationIDs
 */
object NotificationRequestCodes {

    const val BACKUP_CANCEL = -1
    const val CALL_HANG_UP = -2
    const val HOME_ACTIVITY = -3
    const val LOCK_PASSPHRASE = -4
    const val NEW_CONTACTS_SYNCED = -5
    const val RESTORE_CANCEL = -6
    const val SAFE_BACKUP_FAILED = -7
    const val SERVER_MESSAGE = -8
    const val UNSENT_NOTIFICATIONS_CANCEL = -9
    const val UNSENT_NOTIFICATIONS_SEND = -10
    const val WEBCLIENT_SESSIONS = -11
    const val WEBCLIENT_STOP_SESSION = -12

    enum class ConversationNotificationAction(val value: Int) {
        OPEN(0),
        REPLY(1),
        MARK_AS_READ(2),
        ACK(3),
        DEC(4),
        GROUP_CALL_JOIN(5),
        CALL(6),
        OPEN_CALL(7),
        FORWARD_SECURITY(8),
        ;

        companion object {
            const val RESERVED_RANGE = 10
        }
    }

    @JvmStatic
    fun getRequestCodeForConversationNotification(conversationId: ConversationId, action: ConversationNotificationAction): Int =
        NotificationIDs.getNotificationIdForConversation(conversationId) + action.value
}
