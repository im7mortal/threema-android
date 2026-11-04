package ch.threema.app.notifications

import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import testdata.TestData

class NotificationIDsTest {

    @Test
    fun `static notification ids sanity check`() {
        assertTrue(STATIC_IDS.isNotEmpty())
    }

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
        private val STATIC_IDS = NotificationIDs::class.objectInstance!!::class.memberProperties
            .filter { it.isConst && it.visibility == KVisibility.PUBLIC }
            .map { it.javaField!!.getInt(NotificationIDs) }
    }
}
