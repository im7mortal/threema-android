package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.conversion.toGroupIdentity
import ch.threema.app.libthreema.conversion.toGroupModelData
import ch.threema.app.libthreema.conversion.toLibthreemaGroup
import ch.threema.app.libthreema.conversion.toLibthreemaGroupIdentity
import ch.threema.app.libthreema.extensions.getLibthreemaGroupProfilePicture
import ch.threema.app.libthreema.extensions.persistLibthreemaGroupProfilePicture
import ch.threema.app.services.FileService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.UserState
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.Group as LibthreemaGroup
import ch.threema.libthreema.GroupIdentity as LibthreemaGroupIdentity
import ch.threema.libthreema.GroupMembershipFilter as LibthreemaGroupMembershipFilter
import ch.threema.libthreema.GroupProvider as LibthreemaGroupProvider
import ch.threema.libthreema.GroupUpdate as LibthreemaGroupUpdate
import ch.threema.libthreema.GroupWithProfilePicture as LibthreemaGroupWithProfilePicture
import ch.threema.libthreema.ProfilePicture as LibthreemaProfilePicture
import ch.threema.libthreema.ProviderException as LibthreemaProviderException
import ch.threema.libthreema.ThreemaId as LibthreemaIdentity

private val logger = getThreemaLogger("GroupProvider")

class GroupProvider(
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val fileService: FileService,
    override val guaranteedIdentityProvider: GuaranteedIdentityProvider,
) : LibthreemaGroupProvider, IdentityAwareProvider() {

    override fun isPartOfAnyGroup(identity: LibthreemaIdentity, filter: LibthreemaGroupMembershipFilter): Boolean =
        withTimeMeasurement(logger, "isPartOfAnyGroup") {
            requireIdentityIsNotUserIdentity(identity)
            return when (filter) {
                LibthreemaGroupMembershipFilter.ANY -> groupModelRepository.isPartOfAnyGroup(identity)
                LibthreemaGroupMembershipFilter.ONLY_ACTIVE -> groupModelRepository.isPartOfAnyGroupWithUserState(identity, UserState.MEMBER)
            }
        }

    override fun getIdentities(): Set<LibthreemaGroupIdentity> =
        withTimeMeasurement(logger, "getIdentities") {
            groupModelRepository
                .getAllGroupIdentities()
                .map(GroupIdentity::toLibthreemaGroupIdentity)
                .toSet()
        }

    override fun getWithProfilePicture(identity: LibthreemaGroupIdentity): LibthreemaGroupWithProfilePicture? =
        withTimeMeasurement(logger, "getWithProfilePicture") {
            val groupModel = groupModelRepository.getByGroupIdentity(identity.toGroupIdentity())
                ?: return null
            val groupModelData = groupModel.data
                ?: return null

            val group = groupModelData.toLibthreemaGroup()
            val profilePicture = fileService.getLibthreemaGroupProfilePicture(groupModel.getDatabaseId())

            LibthreemaGroupWithProfilePicture(
                group = group,
                profilePicture = profilePicture,
            )
        }

    override fun has(identity: LibthreemaGroupIdentity): Boolean {
        // TODO(ANDR-5127)
        throw NotImplementedError()
    }

    override fun get(identity: LibthreemaGroupIdentity): LibthreemaGroup? {
        // TODO(ANDR-5127)
        throw NotImplementedError()
    }

    override fun update(updates: List<LibthreemaGroupUpdate>) {
        // TODO(ANDR-5127)
        throw NotImplementedError()
    }

    override fun addWithProfilePicture(groups: List<LibthreemaGroupWithProfilePicture>): Unit =
        withTimeMeasurement(logger, "addWithProfilePicture", groups.size) {
            val userIdentity = guaranteedIdentityProvider.getIdentityString()

            // Verify that no group with the same group identity already exists
            val groupIdentities = groups.map { (group, _) -> group.identity.toGroupIdentity() }.toSet()
            if (groupModelRepository.existsAnyByGroupIdentity(groupIdentities)) {
                throw LibthreemaProviderException.ContractViolation("Cannot add group that already exists")
            }

            // Verify that all referenced contacts (either creator or member) exist
            val allCreatorsAndMembers: Set<IdentityString> =
                buildSet {
                    groups.forEach { (group, _) ->
                        if (group.identity.creatorIdentity != userIdentity) {
                            add(group.identity.creatorIdentity)
                        }
                        addAll(group.members)
                    }
                }
            if (contactModelRepository.countExisting(allCreatorsAndMembers).toInt() < allCreatorsAndMembers.size) {
                throw LibthreemaProviderException.ContractViolation("Cannot add group with unknown contact as creator or member")
            }

            // Persist all new groups
            val groupsToCreate = groups
                .map { (group, _) -> group.toGroupModelData() }
                .toSet()
            val newGroupModels = groupModelRepository.persistNewGroups(groupsToCreate)

            // Persist all profile pictures
            val profilePicturesByGroupIdentities = groups.associate { (group, profilePicture) ->
                group.identity.toGroupIdentity() to profilePicture
            }
            for (groupModel in newGroupModels) {
                fileService.persistLibthreemaGroupProfilePicture(
                    groupModel = groupModel,
                    profilePicture = profilePicturesByGroupIdentities[groupModel.groupIdentity],
                )
            }
        }

    override fun setProfilePicture(identity: LibthreemaGroupIdentity, profilePicture: LibthreemaProfilePicture?): Unit =
        withTimeMeasurement(logger, "setProfilePicture") {
            val groupModel = groupModelRepository.getByGroupIdentity(identity.toGroupIdentity())
                ?: throw LibthreemaProviderException.ContractViolation("Cannot set profile picture of unknown group")
            fileService.persistLibthreemaGroupProfilePicture(groupModel, profilePicture)
        }
}
