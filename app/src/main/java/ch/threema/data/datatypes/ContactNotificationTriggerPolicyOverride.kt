package ch.threema.data.datatypes

import java.time.Instant

/**
 *  This class represents the protocol values of `Contact.NotificationTriggerPolicyOverride`.
 */
data class ContactNotificationTriggerPolicyOverride(
    override val policy: ContactNotificationTriggerPolicyOverridePolicy,
    override val expiresAt: Instant?,
) : NotificationTriggerPolicyOverride<ContactNotificationTriggerPolicyOverridePolicy>

enum class ContactNotificationTriggerPolicyOverridePolicy(val serializedValue: Int) {
    /**
     * A notification is never triggered.
     */
    NEVER(0),
    ;

    companion object {
        @JvmStatic
        fun deserialize(serializedValue: Int): ContactNotificationTriggerPolicyOverridePolicy? =
            entries.find { it.serializedValue == serializedValue }
    }
}
