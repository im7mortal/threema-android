package ch.threema.app.conversations

import androidx.annotation.StringRes
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase
import ch.threema.data.datatypes.ConversationId
import ch.threema.storage.models.ConversationModel
import java.io.File

sealed interface ConversationsViewEvent {
    data class OpenConversationActionDialog(val conversationModel: ConversationModel) : ConversationsViewEvent
    data class ConversationArchived(val conversationModel: ConversationModel) : ConversationsViewEvent

    /**
     *  @param targetValueIsMarkedAsPrivate Whether the intent is to mark the conversation as private, or un-mark it
     */
    data class LockMechanismRequiredToUpdatePrivateConversationMark(val targetValueIsMarkedAsPrivate: Boolean) : ConversationsViewEvent

    data class ConfirmationRequiredToMarkConversationAsPrivate(val conversationId: ConversationId) : ConversationsViewEvent
    data object ConversationMarkAsPrivateSuccess : ConversationsViewEvent

    data object UnlockRequiredToUnmarkConversationAsPrivate : ConversationsViewEvent
    data object ConversationUnmarkAsPrivateSuccess : ConversationsViewEvent

    data object LockMechanismRequiredToHidePrivateConversations : ConversationsViewEvent
    data object UnlockRequiredToShowPrivateConversations : ConversationsViewEvent

    data class ConfirmationRequiredToEmptyConversation(val conversationId: ConversationId) : ConversationsViewEvent

    data class ConfirmationRequiredToDeleteContactConversation(val conversationId: ConversationId) : ConversationsViewEvent
    data class ConfirmationRequiredToDeleteDistributionListConversation(val conversationId: ConversationId) : ConversationsViewEvent

    data object StoragePermissionRequiredToShareConversation : ConversationsViewEvent
    data class OnShareConversation(val conversationId: ConversationId) : ConversationsViewEvent
    data class OnConversationFileReadyForSharing(val file: File) : ConversationsViewEvent
    data object OnFailedToCreateConversationFileForSharing : ConversationsViewEvent

    data object OnLeavingGroup : ConversationsViewEvent
    data object OnLeaveGroupFailedInternally : ConversationsViewEvent
    data class OnLeaveGroupCompleted(val result: GroupFlowResult) : ConversationsViewEvent

    data object OnDisbandingGroup : ConversationsViewEvent
    data object OnDisbandGroupFailedInternally : ConversationsViewEvent
    data class OnDisbandGroupCompleted(val result: GroupFlowResult) : ConversationsViewEvent

    data object OnRemoveGroupFailedInternally : ConversationsViewEvent
    data object OnRemovingGroup : ConversationsViewEvent
    data class OnRemoveGroupCompleted(val result: GroupFlowResult) : ConversationsViewEvent

    data object OnSystemLockWasRemoved : ConversationsViewEvent

    data class OnSupportContactAvailable(val conversationId: ConversationId) : ConversationsViewEvent
    data class OnSupportContactUnavailable(@StringRes val message: Int) : ConversationsViewEvent

    data object OnEmptyingConversation : ConversationsViewEvent
    data class OnEmptyingConversationResult(val result: EmptyOrDeleteConversationsUseCase.Result) : ConversationsViewEvent

    data object OnDeletingConversation : ConversationsViewEvent
    data class OnDeletingConversationResult(val result: EmptyOrDeleteConversationsUseCase.Result) : ConversationsViewEvent

    data object UpdateWidgets : ConversationsViewEvent

    data object InternalError : ConversationsViewEvent
}
