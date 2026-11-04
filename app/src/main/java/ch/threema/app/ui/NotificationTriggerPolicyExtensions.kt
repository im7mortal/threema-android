package ch.threema.app.ui

import ch.threema.app.R
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverridePolicy
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import ch.threema.domain.types.Identity
import ch.threema.domain.types.Mention
import java.time.Instant

/**
 * Check whether the mute applies at the given [instant].
 *
 * Note that only the expiration date is checked and the result is independent of the policy.
 */
fun NotificationTriggerPolicyOverride<*>.muteAppliesAt(instant: Instant): Boolean =
    expiresAt == null || instant.isBefore(expiresAt)

fun NotificationTriggerPolicyOverride<*>.getIconResAt(instant: Instant): Int? = when (this) {
    is ContactNotificationTriggerPolicyOverride -> {
        if (muteAppliesAt(instant)) {
            R.drawable.ic_dnd_filled
        } else {
            null
        }
    }

    is GroupNotificationTriggerPolicyOverride -> {
        if (muteAppliesAt(instant)) {
            when (policy) {
                GroupNotificationTriggerPolicyOverridePolicy.MENTIONED -> R.drawable.ic_dnd_mention_black_18dp
                GroupNotificationTriggerPolicyOverridePolicy.NEVER -> R.drawable.ic_dnd_filled
            }
        } else {
            null
        }
    }
}

fun NotificationTriggerPolicyOverride<*>.muteAppliesToMessageAt(message: String, instant: Instant, myIdentity: Identity): Boolean =
    when (this) {
        is ContactNotificationTriggerPolicyOverride -> {
            when (policy) {
                ContactNotificationTriggerPolicyOverridePolicy.NEVER -> {
                    // In case the policy indicates that we should not show a notification for any message, we just need to check whether it applies
                    // at the given instant.
                    muteAppliesAt(instant)
                }
            }
        }

        is GroupNotificationTriggerPolicyOverride -> {
            if (!muteAppliesAt(instant)) {
                return false
            }
            when (policy) {
                GroupNotificationTriggerPolicyOverridePolicy.MENTIONED -> {
                    Mention.findMatches(message)
                        .none { match ->
                            match.mention.includes(myIdentity)
                        }
                }
                GroupNotificationTriggerPolicyOverridePolicy.NEVER -> true
            }
        }
    }
