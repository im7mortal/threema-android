package ch.threema.app.usecases.conversations

import ch.threema.app.groupflows.GroupDisbandIntent
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.groupflows.GroupLeaveIntent
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase.Result.Completed.OperationResult
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.DistributionListModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("EmptyOrDeleteConversationsUseCase")

/**
 * Empty or delete conversations.
 *
 * ##### Behavior with [Mode.EMPTY]:
 *
 * Every message associated with the given conversation(s) is removed. See [ConversationService.empty] for details.
 *
 * ##### Behavior with [Mode.DELETE] depends on the conversation type:
 *
 * **Contact conversations**
 *
 * - Delete conversation, but not contact
 *
 * **Group conversations**
 *
 * - User left: delete conversation and group
 * - User is creator: dissolve and delete group
 * - User is not creator: leave and delete group
 *
 * **Distribution list conversations**
 *
 * - Delete distribution list
 *
 * @return Either [Result.Completed] or [Result.UnknownConversation]. A value of type [Result.Completed] can be used to check the individual results
 * for every given conversation. It is possible to receive a [Result.Completed] where all individual empty/delete operations failed.
 */
class EmptyOrDeleteConversationsUseCase(
    private val dispatcherProvider: DispatcherProvider,
    private val conversationService: ConversationService,
    private val distributionListService: DistributionListService,
    private val groupFlowDispatcher: GroupFlowDispatcher,
    private val identityProvider: IdentityProvider,
) {

    suspend fun call(conversationIds: List<ConversationId>, mode: Mode): Result = withContext(dispatcherProvider.io) {
        val conversations = buildList {
            conversationIds.forEach { conversationId ->
                conversationService.get(conversationId)
                    ?.let(::add)
                    ?: run {
                        logger.error("Conversation {} not found", conversationId)
                        return@withContext Result.UnknownConversation
                    }
            }
        }

        val conversationResults: Map<ConversationId, OperationResult> =
            when (mode) {
                Mode.EMPTY -> emptyConversations(conversations)
                Mode.DELETE -> deleteConversations(conversations)
            }

        conversations.forEach { conversation ->
            conversationService.refresh(conversation.messageReceiver)
        }

        return@withContext Result.Completed(conversationResults)
    }

    private fun emptyConversations(conversations: List<ConversationModel>): Map<ConversationId, OperationResult> {
        conversations.forEach { conversation ->
            logger.info("Emptying conversation {}", conversation.id)
            val removedMessagesCount = conversationService.empty(
                /* conversation = */
                conversation,
                /* silentMessageUpdate = */
                true,
            )
            logger.info("Removed {} messages for conversation {}", removedMessagesCount, conversation.id)
        }
        // Emptying a conversation can only fail if it does not exist. This was validated in a previous step.
        return conversations.associate { conversation ->
            conversation.id to OperationResult.Success
        }
    }

    private suspend fun deleteConversations(conversations: List<ConversationModel>): Map<ConversationId, OperationResult> =
        conversations.associate { conversation ->
            logger.info("Deleting conversation {}", conversation.id)
            val operationResult = when (val conversationId = conversation.id) {
                is ContactConversationId -> deleteContactConversation(conversationId)
                is GroupConversationId -> deleteGroupConversation(groupModel = conversation.groupModel!!)
                is DistributionListConversationId -> deleteDistributionListConversation(distributionListModel = conversation.distributionList!!)
            }
            conversation.id to operationResult
        }

    private fun deleteContactConversation(conversationId: ContactConversationId): OperationResult {
        conversationService.delete(conversationId.identity)
        // Deleting a contact conversation can only fail if it does not exist. This was validated in a previous step
        return OperationResult.Success
    }

    private suspend fun deleteGroupConversation(groupModel: GroupModel): OperationResult {
        val groupModelData: GroupModelData? = groupModel.data
        if (groupModelData == null) {
            logger.error("Group is already deleted as the group model data is null")
            return OperationResult.Failure
        }

        val userIdentity: IdentityString? = identityProvider.getIdentityString()
        if (userIdentity == null) {
            logger.error("Users identity is missing")
            return OperationResult.Failure
        }

        // Decide whether the group must be additionally left or disbanded
        val groupRemoveDeferred: Deferred<GroupFlowResult> =
            when {
                !groupModelData.isMember -> {
                    // In the case where the user is not a member anymore, we can just remove the group.
                    groupFlowDispatcher.runRemoveGroupFlow(
                        groupModel = groupModel,
                    )
                }
                userIdentity == groupModelData.groupIdentity.creatorIdentity -> {
                    // In the case where the user is the creator, we need to disband and remove the group.
                    groupFlowDispatcher.runDisbandGroupFlow(
                        intent = GroupDisbandIntent.DISBAND_AND_REMOVE,
                        groupModel = groupModel,
                    )
                }
                else -> {
                    // Otherwise, we need to leave and remove the group.
                    groupFlowDispatcher.runLeaveGroupFlow(
                        intent = GroupLeaveIntent.LEAVE_AND_REMOVE,
                        groupModel = groupModel,
                    )
                }
            }

        return try {
            val groupFlowResult = groupRemoveDeferred.await()
            when (groupFlowResult) {
                is GroupFlowResult.Success -> OperationResult.Success
                is GroupFlowResult.Failure -> OperationResult.Failure
            }
        } catch (e: CancellationException) {
            // throws if the current coroutine was canceled
            currentCoroutineContext().ensureActive()
            logger.error("Could not remove group (cancelled)", e)
            OperationResult.Failure
        } catch (e: Exception) {
            logger.error("Could not remove group", e)
            OperationResult.Failure
        }
    }

    private fun deleteDistributionListConversation(distributionListModel: DistributionListModel): OperationResult {
        // Note: Distribution list conversations are removed along with the distribution list model
        conversationService.empty(distributionListModel)
        val success = distributionListService.remove(distributionListModel)
        return if (success) OperationResult.Success else OperationResult.Failure
    }

    enum class Mode { EMPTY, DELETE }

    sealed interface Result {

        data object UnknownConversation : Result

        data class Completed(
            val operationResults: Map<ConversationId, OperationResult>,
        ) : Result {

            sealed interface OperationResult {

                data object Success : OperationResult

                data object Failure : OperationResult
            }

            val successCount: Int
                get() = operationResults
                    .values
                    .count { it is OperationResult.Success }
        }
    }
}
