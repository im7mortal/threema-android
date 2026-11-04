package ch.threema.app.usecases.conversation

import ch.threema.app.services.DistributionListService
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.TriggerSource

class SetConversationCategoryUseCase(
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val distributionListService: DistributionListService,
) {
    fun call(
        conversationId: ConversationId,
        conversationCategory: ConversationCategory,
        triggerSource: TriggerSource,
    ) = when (conversationId) {
        is ContactConversationId -> setContactConversationCategory(
            contactConversationId = conversationId,
            conversationCategory = conversationCategory,
            triggerSource = triggerSource,
        )
        is GroupConversationId -> setGroupConversationCategory(
            groupConversationId = conversationId,
            conversationCategory = conversationCategory,
            triggerSource = triggerSource,
        )
        is DistributionListConversationId -> setDistributionListConversationCategory(
            distributionListConversationId = conversationId,
            conversationCategory = conversationCategory,
        )
    }

    private fun setContactConversationCategory(
        contactConversationId: ContactConversationId,
        conversationCategory: ConversationCategory,
        triggerSource: TriggerSource,
    ) {
        val contactModel = contactModelRepository
            .getByIdentity(contactConversationId.identity)
            ?: return

        when (triggerSource) {
            TriggerSource.LOCAL, TriggerSource.REMOTE -> contactModel.setConversationCategoryFromLocalOrRemote(conversationCategory)
            TriggerSource.SYNC -> contactModel.setConversationCategoryFromSync(conversationCategory)
        }
    }

    private fun setGroupConversationCategory(
        groupConversationId: GroupConversationId,
        conversationCategory: ConversationCategory,
        triggerSource: TriggerSource,
    ) {
        val groupModel = groupModelRepository
            .getByGroupDatabaseId(groupConversationId.groupDatabaseId)
            ?: return

        when (triggerSource) {
            TriggerSource.LOCAL, TriggerSource.REMOTE -> groupModel.setConversationCategoryFromLocalOrRemote(conversationCategory)
            TriggerSource.SYNC -> groupModel.setConversationCategoryFromSync(conversationCategory)
        }
    }

    private fun setDistributionListConversationCategory(
        distributionListConversationId: DistributionListConversationId,
        conversationCategory: ConversationCategory,
    ) {
        val distributionListModel = distributionListService
            .getById(distributionListConversationId.distributionListId)
            ?: return

        distributionListService.setConversationCategory(
            distributionListModel,
            conversationCategory,
        )
    }
}
