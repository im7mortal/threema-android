package ch.threema.data.datatypes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactNotificationTriggerPolicyOverridePolicyTest {

    @Test
    fun `serialize contact notification trigger policy override policy`() {
        assertEquals(0, ContactNotificationTriggerPolicyOverridePolicy.NEVER.serializedValue)
    }

    @Test
    fun `deserialize contact notification trigger policy override policy`() {
        assertEquals(
            expected = ContactNotificationTriggerPolicyOverridePolicy.NEVER,
            actual = ContactNotificationTriggerPolicyOverridePolicy.deserialize(0),
        )
        assertNull(ContactNotificationTriggerPolicyOverridePolicy.deserialize(1))
        assertNull(ContactNotificationTriggerPolicyOverridePolicy.deserialize(-1))
    }
}
