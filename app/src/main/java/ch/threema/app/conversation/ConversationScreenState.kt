package ch.threema.app.conversation

import androidx.compose.runtime.Immutable
import ch.threema.app.usecases.conversations.AvatarIteration
import ch.threema.app.usecases.groups.GroupDisplayName
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ConversationId
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel

@Immutable
data class ConversationScreenState(
    val conversationId: ConversationId,
    val secureConversationState: SecureConversationState,
    val hasInitialFocus: Boolean,
    val text: String,
    val receiverState: ConversationReceiverState,
) {

    val shouldShowAvatar: Boolean
        get() = when (receiverState) {
            is ConversationReceiverState.Contact -> true
            is ConversationReceiverState.Group -> true
            is ConversationReceiverState.DistributionList -> !receiverState.isAdHoc
            is ConversationReceiverState.Unknown -> false
        }
}

@Immutable
sealed interface ConversationReceiverState {

    val avatarIteration: AvatarIteration

    @Immutable
    data class Contact(
        val displayName: String,
        val showIdentityTypeBadge: Boolean,
        val verificationLevel: VerificationLevel,
        val workVerificationLevel: WorkVerificationLevel,
        override val avatarIteration: AvatarIteration,
        val availabilityStatus: AvailabilityStatus?,
    ) : ConversationReceiverState

    @Immutable
    data class Group(
        val displayName: GroupDisplayName,
        val members: String,
        val userIsMember: Boolean,
        override val avatarIteration: AvatarIteration,
    ) : ConversationReceiverState

    @Immutable
    data class DistributionList(
        val displayName: String,
        val members: String,
        val isAdHoc: Boolean,
    ) : ConversationReceiverState {

        override val avatarIteration: AvatarIteration = AvatarIteration.initial
    }

    /**
     * This receiver state is used if the contact, group, or distribution list of the shown conversation does not exist.
     */
    @Immutable
    data object Unknown : ConversationReceiverState {

        override val avatarIteration: AvatarIteration = AvatarIteration.initial
    }
}
