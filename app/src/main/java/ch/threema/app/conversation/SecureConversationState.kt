package ch.threema.app.conversation

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SecureConversationState {

    val showConversationContent: Boolean

    val showMissingAppLockContent: Boolean
        get() = this is Private && !appLockConfigured

    @Immutable
    data object NotPrivate : SecureConversationState {

        override val showConversationContent: Boolean = true
    }

    @Immutable
    @ConsistentCopyVisibility
    data class Private private constructor(
        val unlocked: Boolean,
        val appLockConfigured: Boolean,
    ) : SecureConversationState {

        override val showConversationContent: Boolean = unlocked

        companion object {

            fun locked(appLockConfigured: Boolean) =
                Private(
                    unlocked = false,
                    appLockConfigured = appLockConfigured,
                )

            fun unlocked() =
                Private(
                    unlocked = true,
                    appLockConfigured = true,
                )
        }
    }
}
