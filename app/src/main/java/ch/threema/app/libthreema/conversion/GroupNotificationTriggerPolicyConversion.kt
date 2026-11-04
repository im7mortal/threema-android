package ch.threema.app.libthreema.conversion

import ch.threema.common.toEpochMilliInstant
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.libthreema.GroupNotificationTriggerPolicyOverride as LibthreemaGroupNotificationTriggerPolicyOverride
import ch.threema.libthreema.GroupNotificationTriggerPolicyOverridePolicy as LibthreemaGroupNotificationTriggerPolicyOverridePolicy

fun GroupNotificationTriggerPolicyOverride.toLibthreemaGroupNotificationTriggerPolicy(): LibthreemaGroupNotificationTriggerPolicyOverride? =
    LibthreemaGroupNotificationTriggerPolicyOverride(
        policy = when (this.policy) {
            GroupNotificationTriggerPolicyOverridePolicy.MENTIONED -> LibthreemaGroupNotificationTriggerPolicyOverridePolicy.MENTIONED
            GroupNotificationTriggerPolicyOverridePolicy.NEVER -> LibthreemaGroupNotificationTriggerPolicyOverridePolicy.NEVER
        },
        expiresAt = this.expiresAt?.toEpochMilli()?.toULong(),
    )

fun LibthreemaGroupNotificationTriggerPolicyOverride.toGroupNotificationTriggerPolicy(): GroupNotificationTriggerPolicyOverride =
    GroupNotificationTriggerPolicyOverride(
        policy = when (this.policy) {
            LibthreemaGroupNotificationTriggerPolicyOverridePolicy.MENTIONED -> GroupNotificationTriggerPolicyOverridePolicy.MENTIONED
            LibthreemaGroupNotificationTriggerPolicyOverridePolicy.NEVER -> GroupNotificationTriggerPolicyOverridePolicy.NEVER
        },
        expiresAt = this.expiresAt?.toEpochMilliInstant(),
    )
