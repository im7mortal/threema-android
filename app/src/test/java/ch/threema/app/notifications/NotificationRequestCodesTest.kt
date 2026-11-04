package ch.threema.app.notifications

import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationRequestCodesTest {

    @Test
    fun `static request codes sanity check`() {
        assertTrue(REQUEST_CODES.isNotEmpty())
    }

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
        private val REQUEST_CODES = NotificationRequestCodes::class.objectInstance!!::class.memberProperties
            .filter { it.isConst && it.visibility == KVisibility.PUBLIC }
            .map { it.javaField!!.getInt(NotificationIDs) }
    }
}
