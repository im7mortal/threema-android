package ch.threema.app.activities.starred.models

import androidx.compose.runtime.Immutable
import ch.threema.android.ResolvableString
import ch.threema.app.usecases.groups.GroupDisplayName
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.Mention
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.MessageModel

/**
 *  Can be marked as immutable because we never mutate [messageModel].
 */
@Immutable
sealed interface StarredMessageUiModel {

    val uid: MessageUid
    val conversationId: ConversationId
    val messageModel: MessageModel
    val messageContent: ResolvableString?
    val mentionNames: Map<Mention, ResolvableString>
    val sender: ConversationParticipant
    val isPrivate: Boolean

    @Immutable
    data class StarredContactMessage(
        override val uid: MessageUid,
        override val conversationId: ContactConversationId,
        override val messageModel: MessageModel,
        override val messageContent: ResolvableString?,
        override val mentionNames: Map<Mention, ResolvableString>,
        override val sender: ConversationParticipant,
        override val isPrivate: Boolean,
        val receiver: ConversationParticipant,
        val showIdentityTypeBadge: Boolean,
    ) : StarredMessageUiModel

    @Immutable
    data class StarredGroupMessage(
        override val uid: MessageUid,
        override val conversationId: GroupConversationId,
        override val messageModel: MessageModel,
        override val messageContent: ResolvableString?,
        override val mentionNames: Map<Mention, ResolvableString>,
        override val sender: ConversationParticipant,
        override val isPrivate: Boolean,
        val groupDisplayName: GroupDisplayName?,
    ) : StarredMessageUiModel
}

sealed interface ConversationParticipant {

    val identity: Identity

    data class Contact(
        override val identity: Identity,
        val firstname: String?,
        val lastname: String?,
    ) : ConversationParticipant

    data class Me(
        override val identity: Identity,
    ) : ConversationParticipant
}
