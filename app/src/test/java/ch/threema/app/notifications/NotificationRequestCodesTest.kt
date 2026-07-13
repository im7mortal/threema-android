package ch.threema.app.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationRequestCodesTest {

    @Test
    fun `static request codes are valid`() {
        REQUEST_CODES.forEach { requestCode ->
            assertTrue(requestCode < 0)
        }
    }

    @Test
    fun `static request codes are distinct`() {
        assertEquals(
            REQUEST_CODES.distinct(),
            REQUEST_CODES,
        )
    }

    @Test
    fun `actions have valid values`() {
        NotificationRequestCodes.ConversationNotificationAction.entries.forEach { action ->
            assertTrue(action.value >= 0)
            assertTrue(action.value < NotificationRequestCodes.ConversationNotificationAction.RESERVED_RANGE)
        }
    }

    @Test
    fun `actions have distinct values`() {
        val values = NotificationRequestCodes.ConversationNotificationAction.entries.map { it.value }
        assertEquals(
            values.distinct(),
            values,
        )
    }

    @Test
    fun `reserved range is large enough`() {
        assertTrue(
            NotificationRequestCodes.ConversationNotificationAction.entries.size <=
                NotificationRequestCodes.ConversationNotificationAction.RESERVED_RANGE,
        )
    }

    companion object {
        private val REQUEST_CODES = listOf(
            NotificationRequestCodes.BACKUP_CANCEL,
            NotificationRequestCodes.CALL_HANG_UP,
            NotificationRequestCodes.HOME_ACTIVITY,
            NotificationRequestCodes.LOCK_PASSPHRASE,
            NotificationRequestCodes.NEW_CONTACTS_SYNCED,
            NotificationRequestCodes.RESTORE_CANCEL,
            NotificationRequestCodes.SAFE_BACKUP_FAILED,
            NotificationRequestCodes.SERVER_MESSAGE,
            NotificationRequestCodes.UNSENT_NOTIFICATIONS_CANCEL,
            NotificationRequestCodes.UNSENT_NOTIFICATIONS_SEND,
            NotificationRequestCodes.WEBCLIENT_SESSIONS,
            NotificationRequestCodes.WEBCLIENT_STOP_SESSION,
        )
    }
}
