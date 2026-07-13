package ch.threema.app.ui

import ch.threema.app.services.ContactService
import ch.threema.common.minus
import ch.threema.common.plus
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverridePolicy
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import testdata.TestData

class NotificationTriggerPolicyExtensionsTest {
    private val myIdentity = TestData.Identities.ME.value
    private val messageWithoutMention = "This is a simple message."
    private val messageWithAllMention = "This is a message for @[${ContactService.ALL_USERS_PLACEHOLDER_ID}]."
    private val messageWithMention = "This is a message for @[$myIdentity]."

    @Test
    fun `contact notification trigger policy mute until applies at`() {
        val expiresAt = Instant.ofEpochMilli(42)
        val notificationTriggerPolicyOverride = ContactNotificationTriggerPolicyOverride(
            policy = ContactNotificationTriggerPolicyOverridePolicy.NEVER,
            expiresAt = expiresAt,
        )

        assertTrue(notificationTriggerPolicyOverride.muteAppliesAt(expiresAt - 1.seconds))
        assertFalse(notificationTriggerPolicyOverride.muteAppliesAt(expiresAt))
        assertFalse(notificationTriggerPolicyOverride.muteAppliesAt(expiresAt + 1.seconds))
    }

    @Test
    fun `contact notification trigger policy mute forever applies at`() {
        val notificationTriggerPolicyOverride = ContactNotificationTriggerPolicyOverride(
            policy = ContactNotificationTriggerPolicyOverridePolicy.NEVER,
            expiresAt = null,
        )

        assertTrue(notificationTriggerPolicyOverride.muteAppliesAt(Instant.EPOCH))
        assertTrue(notificationTriggerPolicyOverride.muteAppliesAt(Instant.MAX))
    }

    @Test
    fun `group notification trigger policy mute until applies at`() {
        val expiresAt = Instant.ofEpochMilli(42)
        val notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.NEVER,
            expiresAt = expiresAt,
        )

        assertTrue(notificationTriggerPolicyOverride.muteAppliesAt(expiresAt - 1.seconds))
        assertFalse(notificationTriggerPolicyOverride.muteAppliesAt(expiresAt))
        assertFalse(notificationTriggerPolicyOverride.muteAppliesAt(expiresAt + 1.seconds))
    }

    @Test
    fun `group notification trigger policy mute forever applies at`() {
        val notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.NEVER,
            expiresAt = null,
        )

        assertTrue(notificationTriggerPolicyOverride.muteAppliesAt(Instant.EPOCH))
        assertTrue(notificationTriggerPolicyOverride.muteAppliesAt(Instant.MAX))
    }

    @Test
    fun `group notification trigger policy mute except mentions until applies at`() {
        val expiresAt = Instant.ofEpochMilli(42)
        val notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
            expiresAt = expiresAt,
        )

        assertTrue(notificationTriggerPolicyOverride.muteAppliesAt(expiresAt - 1.seconds))
        assertFalse(notificationTriggerPolicyOverride.muteAppliesAt(expiresAt))
        assertFalse(notificationTriggerPolicyOverride.muteAppliesAt(expiresAt + 1.seconds))
    }

    @Test
    fun `group notification trigger policy mute except mentions forever applies at`() {
        val notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
            expiresAt = null,
        )

        assertTrue(notificationTriggerPolicyOverride.muteAppliesAt(Instant.EPOCH))
        assertTrue(notificationTriggerPolicyOverride.muteAppliesAt(Instant.MAX))
    }

    @Test
    fun `contact notification trigger policy mute until applies for all messages`() {
        val expiresAt = Instant.ofEpochMilli(42)
        val notificationTriggerPolicyOverride = ContactNotificationTriggerPolicyOverride(
            policy = ContactNotificationTriggerPolicyOverridePolicy.NEVER,
            expiresAt = expiresAt,
        )
        val allMessages = listOf(
            messageWithoutMention,
            messageWithAllMention,
            messageWithMention,
        )

        allMessages.forEach { message ->
            assertTrue(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = expiresAt - 1.seconds,
                    myIdentity = myIdentity,
                ),
            )
            assertFalse(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = expiresAt,
                    myIdentity = myIdentity,
                ),
            )
            assertFalse(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = expiresAt + 1.seconds,
                    myIdentity = myIdentity,
                ),
            )
        }
    }

    @Test
    fun `contact notification trigger policy mute forever applies for all messages`() {
        val notificationTriggerPolicyOverride = ContactNotificationTriggerPolicyOverride(
            policy = ContactNotificationTriggerPolicyOverridePolicy.NEVER,
            expiresAt = null,
        )
        val allMessages = listOf(
            messageWithoutMention,
            messageWithAllMention,
            messageWithMention,
        )

        allMessages.forEach { message ->
            assertTrue(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = Instant.EPOCH,
                    myIdentity = myIdentity,
                ),
            )
            assertTrue(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = Instant.MAX,
                    myIdentity = myIdentity,
                ),
            )
        }
    }

    @Test
    fun `group notification trigger policy mute until applies for all messages`() {
        val expiresAt = Instant.ofEpochMilli(42)
        val notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.NEVER,
            expiresAt = expiresAt,
        )
        val allMessages = listOf(
            messageWithoutMention,
            messageWithAllMention,
            messageWithMention,
        )

        allMessages.forEach { message ->
            assertTrue(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = expiresAt - 1.seconds,
                    myIdentity = myIdentity,
                ),
            )
            assertFalse(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = expiresAt,
                    myIdentity = myIdentity,
                ),
            )
            assertFalse(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = expiresAt + 1.seconds,
                    myIdentity = myIdentity,
                ),
            )
        }
    }

    @Test
    fun `group notification trigger policy mute until applies for mentioned messages`() {
        val expiresAt = Instant.ofEpochMilli(42)
        val notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
            expiresAt = expiresAt,
        )
        val allMessages = listOf(
            messageWithoutMention,
            messageWithAllMention,
            messageWithMention,
        )

        assertTrue(
            notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                message = messageWithoutMention,
                instant = expiresAt - 1.seconds,
                myIdentity = myIdentity,
            ),
        )
        assertFalse(
            notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                message = messageWithAllMention,
                instant = expiresAt - 1.seconds,
                myIdentity = myIdentity,
            ),
        )
        assertFalse(
            notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                message = messageWithMention,
                instant = expiresAt - 1.seconds,
                myIdentity = myIdentity,
            ),
        )

        allMessages.forEach { message ->
            assertFalse(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = expiresAt,
                    myIdentity = myIdentity,
                ),
            )
            assertFalse(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = expiresAt + 1.seconds,
                    myIdentity = myIdentity,
                ),
            )
        }
    }

    @Test
    fun `group notification trigger policy mute forever applies for all messages`() {
        val notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.NEVER,
            expiresAt = null,
        )
        val allMessages = listOf(
            messageWithoutMention,
            messageWithAllMention,
            messageWithMention,
        )

        allMessages.forEach { message ->
            assertTrue(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = Instant.EPOCH,
                    myIdentity = myIdentity,
                ),
            )
            assertTrue(
                notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                    message = message,
                    instant = Instant.MAX,
                    myIdentity = myIdentity,
                ),
            )
        }
    }

    @Test
    fun `group notification trigger policy mute forever applies for mentioned messages`() {
        val notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
            expiresAt = null,
        )

        assertTrue(
            notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                message = messageWithoutMention,
                instant = Instant.ofEpochMilli(42),
                myIdentity = myIdentity,
            ),
        )
        assertFalse(
            notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                message = messageWithAllMention,
                instant = Instant.ofEpochMilli(42),
                myIdentity = myIdentity,
            ),
        )
        assertFalse(
            notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                message = messageWithMention,
                instant = Instant.ofEpochMilli(42),
                myIdentity = myIdentity,
            ),
        )
    }
}
