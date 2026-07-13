package ch.threema.data.datatypes

import java.time.Instant

/**
 *  This class represents the protocol values of `Group.NotificationTriggerPolicyOverride`.
 */
data class GroupNotificationTriggerPolicyOverride(
    override val policy: GroupNotificationTriggerPolicyOverridePolicy,
    override val expiresAt: Instant?,
) : NotificationTriggerPolicyOverride<GroupNotificationTriggerPolicyOverridePolicy>

enum class GroupNotificationTriggerPolicyOverridePolicy(val serializedValue: Int) {
    MENTIONED(0),
    NEVER(1),
    ;

    companion object {
        @JvmStatic
        fun deserialize(serializedValue: Int): GroupNotificationTriggerPolicyOverridePolicy? =
            entries.find { it.serializedValue == serializedValue }
    }
}
