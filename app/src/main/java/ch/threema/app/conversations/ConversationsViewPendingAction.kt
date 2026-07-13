package ch.threema.app.conversations

import androidx.compose.runtime.Immutable
import ch.threema.data.datatypes.ConversationId
import ch.threema.storage.models.ConversationModel
import java.io.File

@Immutable
sealed interface ConversationsViewPendingAction {

    @Immutable
    data class MarkConversationAsPrivate(val conversation: ConversationModel) : ConversationsViewPendingAction

    @Immutable
    data class UnmarkConversationAsPrivate(val conversation: ConversationModel) : ConversationsViewPendingAction

    @Immutable
    data class ShareConversation(val conversationId: ConversationId) : ConversationsViewPendingAction

    @Immutable
    data class DeleteSharedConversationFile(val file: File) : ConversationsViewPendingAction
}
