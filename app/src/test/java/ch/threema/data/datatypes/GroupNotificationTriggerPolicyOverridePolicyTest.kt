package ch.threema.data.datatypes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupNotificationTriggerPolicyOverridePolicyTest {

    @Test
    fun `serialize group notification trigger policy override policy`() {
        assertEquals(0, GroupNotificationTriggerPolicyOverridePolicy.MENTIONED.serializedValue)
        assertEquals(1, GroupNotificationTriggerPolicyOverridePolicy.NEVER.serializedValue)
    }

    @Test
    fun `deserialize group notification trigger policy override policy`() {
        assertEquals(
            expected = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
            actual = GroupNotificationTriggerPolicyOverridePolicy.deserialize(0),
        )
        assertEquals(
            expected = GroupNotificationTriggerPolicyOverridePolicy.NEVER,
            actual = GroupNotificationTriggerPolicyOverridePolicy.deserialize(1),
        )
        assertNull(ContactNotificationTriggerPolicyOverridePolicy.deserialize(2))
        assertNull(ContactNotificationTriggerPolicyOverridePolicy.deserialize(-1))
    }
}
