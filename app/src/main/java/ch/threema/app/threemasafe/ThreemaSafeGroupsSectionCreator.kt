package ch.threema.app.threemasafe

import ch.threema.common.toNonNegativeTimestamp
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.GroupId

class ThreemaSafeGroupsSectionCreator(
    private val identityProvider: IdentityProvider,
    private val groupModelRepository: GroupModelRepository,
) {
    fun create(): ThreemaSafeGroupsSection {
        val threemaSafeGroups = groupModelRepository
            .getAll()
            .mapNotNull(GroupModel::data)
            .map(::convertToThreemaSafeGroup)

        return ThreemaSafeGroupsSection.create(threemaSafeGroups)
    }

    private fun convertToThreemaSafeGroup(groupModelData: GroupModelData): ThreemaSafeGroupsSection.ThreemaSafeGroup =
        ThreemaSafeGroupsSection.ThreemaSafeGroup(
            id = groupModelData.getId(),
            creator = groupModelData.groupIdentity.creatorIdentity,
            groupname = groupModelData.name,
            createdAt = groupModelData.createdAt.toEpochMilli().toNonNegativeTimestamp(),
            members = groupModelData.getThreemaSafeMembers(),
            deleted = false,
            lastUpdate = groupModelData.lastUpdate?.toEpochMilli()?.toNonNegativeTimestamp(),
            private = groupModelData.isPrivate(),
        )

    private fun GroupModelData.getId() =
        GroupId(groupIdentity.groupId).toString()

    private fun GroupModelData.getThreemaSafeMembers() =
        if (isMember) {
            otherMembers.toList() + identityProvider.requireIdentityString()
        } else {
            otherMembers.toList()
        }

    private fun GroupModelData.isPrivate() = when (conversationCategory) {
        ConversationCategory.DEFAULT -> false
        ConversationCategory.PROTECTED -> true
    }
}
