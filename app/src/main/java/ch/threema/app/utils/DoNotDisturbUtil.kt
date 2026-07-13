package ch.threema.app.utils

import ch.threema.app.ui.muteAppliesAt
import ch.threema.app.ui.muteAppliesToMessageAt
import ch.threema.common.TimeProvider
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride

abstract class DoNotDisturbUtil(
    private val identityProvider: IdentityProvider,
    private val timeProvider: TimeProvider,
) {
    /**
     * Returns true if the conversation for the provided MessageReceiver is permanently or temporarily muted AT THIS TIME and
     * no intrusive notification should be shown for an incoming message.
     * If a message text is provided it is checked for possible mentions - group messages only
     *
     * @param rawMessageText Text of the incoming message (optional, group messages only)
     */
    fun isMessageMuted(
        notificationTriggerPolicyOverride: NotificationTriggerPolicyOverride<*>?,
        rawMessageText: CharSequence?,
    ): Boolean =
        isMutedByOverrideSetting(notificationTriggerPolicyOverride, rawMessageText) || isDoNotDisturbActive()

    private fun isMutedByOverrideSetting(
        notificationTriggerPolicyOverride: NotificationTriggerPolicyOverride<*>?,
        rawMessageText: CharSequence?,
    ): Boolean {
        if (notificationTriggerPolicyOverride == null) {
            return false
        }
        val myIdentity = identityProvider.getIdentity()?.value
        return if (rawMessageText != null && myIdentity != null) {
            notificationTriggerPolicyOverride.muteAppliesToMessageAt(
                message = rawMessageText.toString(),
                instant = timeProvider.get(),
                myIdentity = myIdentity,
            )
        } else {
            notificationTriggerPolicyOverride.muteAppliesAt(timeProvider.get())
        }
    }

    /**
     * Check if Work DND schedule is currently active
     *
     * @return true if we're currently outside the working hours set by the user and Work DND is currently enabled, false otherwise
     */
    abstract fun isDoNotDisturbActive(): Boolean
}
