package ch.threema.app.usecases.conversation

import ch.threema.app.services.DistributionListService
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository

class GetConversationCategoryUseCaseImpl(
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val distributionListService: DistributionListService,
) : GetConversationCategoryUseCase {

    override fun call(conversationId: ConversationId): ConversationCategory = when (conversationId) {
        is ContactConversationId -> getContactConversationCategory(conversationId)
        is GroupConversationId -> getGroupConversationCategory(conversationId)
        is DistributionListConversationId -> getDistributionListConversationCategory(conversationId)
    }

    private fun getContactConversationCategory(contactConversationId: ContactConversationId): ConversationCategory =
        contactModelRepository
            .getByIdentity(contactConversationId.identity)
            ?.data
            ?.conversationCategory
            ?: ConversationCategory.DEFAULT

    private fun getGroupConversationCategory(groupConversationId: GroupConversationId): ConversationCategory =
        groupModelRepository
            .getByGroupDatabaseId(groupConversationId.groupDatabaseId)
            ?.data
            ?.conversationCategory
            ?: ConversationCategory.DEFAULT

    private fun getDistributionListConversationCategory(distributionListConversationId: DistributionListConversationId): ConversationCategory =
        distributionListService
            .getById(distributionListConversationId.distributionListId)
            ?.conversationCategory
            ?: ConversationCategory.DEFAULT
}
