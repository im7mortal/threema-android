package ch.threema.app.threemasafe

import ch.threema.app.services.DistributionListService
import ch.threema.app.threemasafe.ThreemaSafeDistributionListsSection.ThreemaSafeDistributionList
import ch.threema.common.toByteArray
import ch.threema.common.toHexString
import ch.threema.common.toNonNegativeTimestamp
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.DistributionListModel
import java.nio.ByteOrder

class ThreemaSafeDistributionListsSectionCreator(
    private val distributionListService: DistributionListService,
) {
    fun create(): ThreemaSafeDistributionListsSection {
        val threemaSafeDistributionListModels = distributionListService.getAll(
            object : DistributionListService.DistributionListFilter {
                override fun sortingByDate() = false
                override fun sortingAscending() = false
                override fun showHidden() = false
            },
        )
            .map(::convertToThreemaSafeDistributionList)

        return ThreemaSafeDistributionListsSection.create(threemaSafeDistributionListModels)
    }

    private fun convertToThreemaSafeDistributionList(distributionListModel: DistributionListModel) =
        ThreemaSafeDistributionList(
            id = distributionListModel.id.toByteArray(ByteOrder.LITTLE_ENDIAN).toHexString(),
            name = distributionListModel.name ?: "",
            createdAt = distributionListModel.createdAt.toEpochMilli().toNonNegativeTimestamp(),
            members = distributionListModel.getMembers(),
            lastUpdate = distributionListModel.lastUpdate.toEpochMilli().toNonNegativeTimestamp(),
            private = distributionListModel.isPrivate(),
        )

    private fun DistributionListModel.getMembers(): List<IdentityString> =
        distributionListService
            .getDistributionListIdentities(this)
            .toList()

    private fun DistributionListModel.isPrivate(): Boolean = when (conversationCategory) {
        ConversationCategory.DEFAULT -> false
        ConversationCategory.PROTECTED -> true
    }
}
