package ch.threema.app.libthreema.conversion

import ch.threema.common.toEpochMilliInstant
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverridePolicy
import ch.threema.libthreema.ContactNotificationTriggerPolicyOverride as LibthreemaContactNotificationTriggerPolicy
import ch.threema.libthreema.ContactNotificationTriggerPolicyOverridePolicy as LibthreemaContactNotificationTriggerPolicyOverridePolicy

fun ContactNotificationTriggerPolicyOverride.toLibthreemaContactNotificationTriggerPolicy(): LibthreemaContactNotificationTriggerPolicy? =
    LibthreemaContactNotificationTriggerPolicy(
        policy = when (this.policy) {
            ContactNotificationTriggerPolicyOverridePolicy.NEVER -> LibthreemaContactNotificationTriggerPolicyOverridePolicy.NEVER
        },
        expiresAt = this.expiresAt?.toEpochMilli()?.toULong(),
    )

fun LibthreemaContactNotificationTriggerPolicy.toContactNotificationTriggerPolicyOverride(): ContactNotificationTriggerPolicyOverride =
    ContactNotificationTriggerPolicyOverride(
        policy = when (this.policy) {
            LibthreemaContactNotificationTriggerPolicyOverridePolicy.NEVER -> ContactNotificationTriggerPolicyOverridePolicy.NEVER
        },
        expiresAt = this.expiresAt?.toEpochMilliInstant(),
    )
