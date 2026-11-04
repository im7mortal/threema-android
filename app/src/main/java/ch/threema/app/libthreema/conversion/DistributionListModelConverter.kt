package ch.threema.app.libthreema.conversion

import ch.threema.app.services.DistributionListService
import ch.threema.common.toEpochMilliInstant
import ch.threema.libthreema.DistributionList as LibthreemaDistributionList
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel

class DistributionListModelConverter(
    private val distributionListService: DistributionListService,
) {
    fun toLibthreemaDistributionList(distributionListModel: DistributionListModel): LibthreemaDistributionList =
        LibthreemaDistributionList(
            identity = distributionListModel.id.toULong(),
            name = distributionListModel.name ?: "",
            createdAt = distributionListModel.createdAt?.toEpochMilli()?.toULong() ?: 0u,
            lastUpdateAt = distributionListModel.lastUpdate.toEpochMilli().toULong(),
            members = distributionListService.getMembers(distributionListModel).map(ContactModel::identity).toSet(),
            conversationCategory = distributionListModel.conversationCategory.toLibthreemaConversationCategory(),
            conversationVisibility = distributionListModel.conversationVisibility.toLibthreemaConversationVisibility(),
        )

    fun toDistributionListModel(distributionList: LibthreemaDistributionList): DistributionListModel =
        DistributionListModel()
            .setId(distributionList.identity.toLong())
            .setName(distributionList.name)
            .setCreatedAt(distributionList.createdAt.toEpochMilliInstant())
            .setLastUpdate(distributionList.lastUpdateAt.toEpochMilliInstant())
            .setConversationVisibility(distributionList.conversationVisibility.toConversationVisibility())
            .setConversationCategory(distributionList.conversationCategory.toConversationCategory())
}
