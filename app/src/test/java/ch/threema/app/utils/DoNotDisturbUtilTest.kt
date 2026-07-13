package ch.threema.app.utils

import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.TestTimeProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import testdata.TestData

class DoNotDisturbUtilTest {

    @Test
    fun `message is not muted when neither policy nor DND settings mute it`() {
        val doNotDisturbUtil = createDoNotDisturbUtil(dndActive = false)

        assertFalse(
            doNotDisturbUtil.isMessageMuted(
                notificationTriggerPolicyOverride = null,
                rawMessageText = "Hello",
            ),
        )
    }

    @Test
    fun `message is muted when DND settings mute it`() {
        val doNotDisturbUtil = createDoNotDisturbUtil(dndActive = true)

        assertTrue(
            doNotDisturbUtil.isMessageMuted(
                notificationTriggerPolicyOverride = null,
                rawMessageText = "Hello",
            ),
        )
    }

    @Test
    fun `message is muted when policy applies to message`() {
        val doNotDisturbUtil = createDoNotDisturbUtil(dndActive = false)

        assertTrue(
            doNotDisturbUtil.isMessageMuted(
                notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
                    policy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
                    expiresAt = null,
                ),
                rawMessageText = "Hello @${TestData.Identities.ME.value}",
            ),
        )
    }

    @Test
    fun `message is muted when no message text exists but policy applies now`() {
        val doNotDisturbUtil = createDoNotDisturbUtil(dndActive = false)

        assertTrue(
            doNotDisturbUtil.isMessageMuted(
                notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
                    policy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
                    expiresAt = null,
                ),
                rawMessageText = null,
            ),
        )
    }

    companion object {
        private val timeProvider = TestTimeProvider()

        private fun createDoNotDisturbUtil(dndActive: Boolean) = object : DoNotDisturbUtil(
            identityProvider = TestIdentityProvider(identity = TestData.Identities.ME),
            timeProvider = timeProvider,
        ) {
            override fun isDoNotDisturbActive() = dndActive
        }
    }
}
