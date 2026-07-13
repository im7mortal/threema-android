package ch.threema.protobuf

import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.logging.logAndReportError
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.sync.Group
import ch.threema.protobuf.d2d.sync.GroupKt
import java.time.Instant

private val logger = getThreemaLogger("GroupNotificationTriggerPolicyOverrideConversions")

fun GroupNotificationTriggerPolicyOverride?.toProtobuf(): Group.NotificationTriggerPolicyOverride {
    if (this == null) {
        return GroupKt.notificationTriggerPolicyOverride {
            default = unit {}
        }
    }

    val protobufPolicy = when (policy) {
        GroupNotificationTriggerPolicyOverridePolicy.NEVER -> Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
        GroupNotificationTriggerPolicyOverridePolicy.MENTIONED -> Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.MENTIONED
    }
    val protobufExpiresAt = expiresAt?.toEpochMilli()

    return GroupKt.notificationTriggerPolicyOverride {
        policy = GroupKt.NotificationTriggerPolicyOverrideKt.policy {
            policy = protobufPolicy
            if (protobufExpiresAt != null) {
                expiresAt = protobufExpiresAt
            }
        }
    }
}

fun Group.NotificationTriggerPolicyOverride.toDataType(): GroupNotificationTriggerPolicyOverride? = when (overrideCase) {
    Group.NotificationTriggerPolicyOverride.OverrideCase.DEFAULT -> null
    Group.NotificationTriggerPolicyOverride.OverrideCase.POLICY -> {
        when (policy.policy) {
            Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER -> {
                GroupNotificationTriggerPolicyOverride(
                    policy = GroupNotificationTriggerPolicyOverridePolicy.NEVER,
                    expiresAt = getExpiresAt(),
                )
            }

            Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.MENTIONED -> {
                GroupNotificationTriggerPolicyOverride(
                    policy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
                    expiresAt = getExpiresAt(),
                )
            }

            Group.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.UNRECOGNIZED -> {
                logger.logAndReportError(
                    "Tried converting unrecognized group notification trigger policy override policy: {}",
                    overrideCase,
                )
                null
            }

            null -> {
                logger.warn("Group notification trigger policy override policy is null")
                null
            }
        }
    }

    Group.NotificationTriggerPolicyOverride.OverrideCase.OVERRIDE_NOT_SET -> {
        logger.warn("Group notification trigger policy override is not set")
        null
    }

    null -> null
}

private fun Group.NotificationTriggerPolicyOverride.getExpiresAt(): Instant? =
    if (policy.hasExpiresAt()) {
        Instant.ofEpochMilli(policy.expiresAt)
    } else {
        null
    }
