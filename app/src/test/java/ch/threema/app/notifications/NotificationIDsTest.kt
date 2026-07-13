package ch.threema.app.notifications

import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import testdata.TestData

class NotificationIDsTest {

    @Test
    fun `static notification ids are valid`() {
        STATIC_IDS.forEach { notificationId ->
            assertTrue(notificationId < 0)
        }
    }

    @Test
    fun `static notification ids are distinct`() {
        assertEquals(
            STATIC_IDS.distinct(),
            STATIC_IDS,
        )
    }

    @Test
    fun `get notification id for contact conversation`() {
        assertEquals(
            expected = 688747200,
            actual = NotificationIDs.getNotificationIdForConversation(ContactConversationId(identity = TestData.Identities.OTHER_1.value)),
        )
        assertEquals(
            expected = 377484400,
            actual = NotificationIDs.getNotificationIdForConversation(ContactConversationId(identity = TestData.Identities.OTHER_2.value)),
        )
        assertEquals(
            expected = 66221600,
            actual = NotificationIDs.getNotificationIdForConversation(ContactConversationId(identity = TestData.Identities.OTHER_3.value)),
        )
        assertEquals(
            expected = 424583330,
            actual = NotificationIDs.getNotificationIdForConversation(ContactConversationId(identity = "*ZZZZZZZ")),
        )
    }

    @Test
    fun `get notification id for group conversation`() {
        assertEquals(
            expected = 1000010010,
            actual = NotificationIDs.getNotificationIdForConversation(GroupConversationId(groupDatabaseId = 1L)),
        )
        assertEquals(
            expected = 1000010020,
            actual = NotificationIDs.getNotificationIdForConversation(GroupConversationId(groupDatabaseId = 2L)),
        )
        assertEquals(
            expected = 1474846480,
            actual = NotificationIDs.getNotificationIdForConversation(GroupConversationId(groupDatabaseId = 2147483648L)),
        )
    }

    @Test
    fun `get notification id for distribution list conversation`() {
        assertFailsWith<IllegalArgumentException> {
            NotificationIDs.getNotificationIdForConversation(DistributionListConversationId(distributionListId = 44L))
        }
    }

    companion object {
        private val STATIC_IDS = listOf(
            NotificationIDs.APP_RESTART_NOTIFICATION_ID,
            NotificationIDs.BACKUP_COMPLETION_NOTIFICATION_ID,
            NotificationIDs.BACKUP_NOTIFICATION_ID,
            NotificationIDs.INCOMING_CALL_NOTIFICATION_ID,
            NotificationIDs.INCOMING_GROUP_CALL_NOTIFICATION_ID,
            NotificationIDs.IN_CALL_NOTIFICATION_ID,
            NotificationIDs.MASTER_KEY_LOCKED_NOTIFICATION_ID,
            NotificationIDs.NEW_MESSAGE_LOCKED_NOTIFICATION_ID,
            NotificationIDs.NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID,
            NotificationIDs.NEW_SYNCED_CONTACTS_NOTIFICATION_ID,
            NotificationIDs.ONGOING_GROUP_CALL_NOTIFICATION_ID,
            NotificationIDs.PASSPHRASE_SERVICE_NOTIFICATION_ID,
            NotificationIDs.REMOTE_SECRET_ACTIVE_NOTIFICATION_ID,
            NotificationIDs.RESTORE_COMPLETION_NOTIFICATION_ID,
            NotificationIDs.RESTORE_NOTIFICATION_ID,
            NotificationIDs.SAFE_FAILED_NOTIFICATION_ID,
            NotificationIDs.SERVER_MESSAGE_NOTIFICATION_ID,
            NotificationIDs.THREEMA_PUSH_ACTIVE_NOTIFICATION_ID,
            NotificationIDs.UNSENT_MESSAGE_NOTIFICATION_ID,
            NotificationIDs.VOICE_ACTION_NOTIFICATION_ID,
            NotificationIDs.VOICE_MESSAGE_PLAYER_NOTIFICATION_ID,
            NotificationIDs.WEBCLIENT_ACTIVE_NOTIFICATION_ID,
            NotificationIDs.WEB_RESUME_FAILED_NOTIFICATION_ID,
            NotificationIDs.WORK_SYNC_NOTIFICATION_ID,
        )
    }
}
