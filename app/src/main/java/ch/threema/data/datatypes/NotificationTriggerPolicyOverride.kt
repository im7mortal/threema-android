package ch.threema.data.datatypes

import java.time.Instant

sealed interface NotificationTriggerPolicyOverride<P> {
    val policy: P
    val expiresAt: Instant?
}
