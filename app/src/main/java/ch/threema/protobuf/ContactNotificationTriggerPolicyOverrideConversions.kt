package ch.threema.protobuf

import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverridePolicy
import ch.threema.logging.logAndReportError
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.sync.Contact
import ch.threema.protobuf.d2d.sync.ContactKt
import java.time.Instant

private val logger = getThreemaLogger("ContactNotificationTriggerPolicyOverrideConversions")

fun ContactNotificationTriggerPolicyOverride?.toProtobuf(): Contact.NotificationTriggerPolicyOverride {
    if (this == null) {
        return ContactKt.notificationTriggerPolicyOverride {
            default = unit {}
        }
    }

    val protobufPolicy = when (policy) {
        ContactNotificationTriggerPolicyOverridePolicy.NEVER -> Contact.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
    }
    val protobufExpiresAt = expiresAt?.toEpochMilli()

    return ContactKt.notificationTriggerPolicyOverride {
        policy = ContactKt.NotificationTriggerPolicyOverrideKt.policy {
            policy = protobufPolicy
            if (protobufExpiresAt != null) {
                expiresAt = protobufExpiresAt
            }
        }
    }
}

fun Contact.NotificationTriggerPolicyOverride.toDataType(): ContactNotificationTriggerPolicyOverride? = when (overrideCase) {
    Contact.NotificationTriggerPolicyOverride.OverrideCase.DEFAULT -> null
    Contact.NotificationTriggerPolicyOverride.OverrideCase.POLICY -> {
        when (policy.policy) {
            Contact.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER -> {
                ContactNotificationTriggerPolicyOverride(
                    policy = ContactNotificationTriggerPolicyOverridePolicy.NEVER,
                    expiresAt = getExpiresAt(),
                )
            }

            Contact.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.UNRECOGNIZED -> {
                logger.logAndReportError(
                    "Tried converting unrecognized contact notification trigger policy override policy: {}",
                    overrideCase,
                )
                null
            }

            null -> {
                logger.warn("Contact notification trigger policy override policy is null")
                null
            }
        }
    }

    Contact.NotificationTriggerPolicyOverride.OverrideCase.OVERRIDE_NOT_SET -> {
        logger.warn("Contact notification trigger policy override is not set")
        null
    }
    null -> null
}

private fun Contact.NotificationTriggerPolicyOverride.getExpiresAt(): Instant? =
    if (policy.hasExpiresAt()) {
        Instant.ofEpochMilli(policy.expiresAt)
    } else {
        null
    }
