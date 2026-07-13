package ch.threema.app.processors.reflectedd2dsync

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.app.utils.AppVersionProvider
import ch.threema.app.utils.ExifInterface
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupState
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupAlreadyExistsException
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.data.repositories.GroupStoreException
import ch.threema.domain.models.UserState
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.logging.logAndReportError
import ch.threema.protobuf.common.Blob
import ch.threema.protobuf.common.DeltaImage
import ch.threema.protobuf.d2d.GroupSync
import ch.threema.protobuf.d2d.sync.ConversationCategory
import ch.threema.protobuf.d2d.sync.ConversationVisibility as ProtocolsConversationVisibility
import ch.threema.protobuf.d2d.sync.Group
import ch.threema.protobuf.toDataType
import java.time.Instant
import java.util.Collections
import okhttp3.OkHttpClient

private val logger = getThreemaLogger("ReflectedGroupSyncTask")

class ReflectedGroupSyncTask(
    private val groupSync: GroupSync,
    private val groupModelRepository: GroupModelRepository,
    private val groupService: GroupService,
    private val fileService: FileService,
    private val okHttpClient: OkHttpClient,
    private val serverAddressProvider: ServerAddressProvider,
    private val symmetricEncryptionService: SymmetricEncryptionService,
    private val multiDeviceManager: MultiDeviceManager,
    private val conversationCategoryService: ConversationCategoryService,
    private val globalEventBuses: GlobalEventBuses,
    userService: UserService,
) {
    private val myIdentity by lazy { userService.identity }

    fun run() {
        when (groupSync.actionCase) {
            GroupSync.ActionCase.CREATE -> handleGroupCreate(groupSync.create)
            GroupSync.ActionCase.UPDATE -> handleGroupUpdate(groupSync.update)
            GroupSync.ActionCase.DELETE -> handleGroupDelete(groupSync.delete)
            GroupSync.ActionCase.ACTION_NOT_SET -> logger.warn("No action set for group sync")
            null -> logger.warn("Action is null for contact sync")
        }
    }

    private fun handleGroupCreate(groupCreate: GroupSync.Create) {
        logger.info("Processing reflected group create")

        val groupModelData = groupCreate.group.toNewGroupModelData()

        val groupModel = try {
            groupModelRepository.createFromSync(groupModelData)
        } catch (e: Exception) {
            when (e) {
                is GroupStoreException -> logger.error("Could not store group", e)
                is GroupAlreadyExistsException -> logger.error("Group already exists")
                else -> throw e
            }
            return
        }

        applyProfilePicture(groupCreate.group, groupModel)

        logger.info("New group successfully created from sync")
    }

    private fun handleGroupUpdate(groupUpdate: GroupSync.Update) {
        logger.info("Processing reflected group update")

        val group = groupUpdate.group
        val groupModel = groupModelRepository.getByGroupIdentity(group.groupIdentity.convert())
        if (groupModel == null) {
            logger.error("Received group update for unknown group")
            return
        }

        applyName(group, groupModel)
        applyUserState(group, groupModel)
        applyNotificationTriggerPolicyOverride(group, groupModel)
        applyProfilePicture(group, groupModel)
        applyMembers(group, groupUpdate.memberStateChangesMap, groupModel)
        applyConversationCategory(group, groupModel)
        applyConversationVisibility(group, groupModel)
    }

    private fun handleGroupDelete(groupDelete: GroupSync.Delete) {
        logger.info("Processing reflected group delete")

        val groupIdentity = groupDelete.groupIdentity.convert()
        val groupModel = groupModelRepository.getByGroupIdentity(groupIdentity) ?: run {
            logger.error("Cannot delete unknown group")
            return
        }

        groupService.removeGroupBelongings(groupModel, TriggerSource.SYNC)
        groupModelRepository.persistRemovedGroup(groupIdentity)

        logger.info("Deleted group")
    }

    private fun applyName(group: Group, groupModel: GroupModel) {
        if (group.hasName()) {
            groupModel.setNameFromSync(group.name)
        }
    }

    private fun applyUserState(group: Group, groupModel: GroupModel) {
        if (group.hasUserState()) {
            val userState = group.userState.convert() ?: return

            groupModel.setUserStateFromSync(userState)
        }
    }

    private fun applyNotificationTriggerPolicyOverride(group: Group, groupModel: GroupModel) {
        if (group.hasNotificationTriggerPolicyOverride()) {
            groupModel.setNotificationTriggerPolicyOverrideFromSync(
                group.notificationTriggerPolicyOverride.toDataType(),
            )
        }
    }

    private fun applyProfilePicture(group: Group, groupModel: GroupModel) {
        if (group.hasProfilePicture()) {
            when (group.profilePicture.imageCase) {
                DeltaImage.ImageCase.REMOVED -> removeGroupAvatar(groupModel)
                DeltaImage.ImageCase.UPDATED -> group.profilePicture.updated.blob.loadGroupProfilePictureAndMarkAsDone(groupModel)
                DeltaImage.ImageCase.IMAGE_NOT_SET -> logger.warn("Profile picture image case not set")
                null -> logger.warn("Profile picture image case is null")
            }
        }
    }

    private fun applyMembers(
        group: Group,
        memberStateMap: Map<IdentityString, GroupSync.Update.MemberStateChange>,
        groupModel: GroupModel,
    ) {
        val previousGroupState = groupModel.getGroupState()

        // Abort if the member identities are not set
        if (!group.hasMemberIdentities()) {
            if (memberStateMap.isNotEmpty()) {
                logger.warn("Received member state changes but no updated member identities")
            }
            return
        }

        // Note that the member list should not contain the user and the creator.
        if (group.memberIdentities.identitiesList.contains(myIdentity!!)) {
            logger.logAndReportError("Member identities of a group should not contain the user identity")
        }
        if (group.memberIdentities.identitiesList.contains(group.groupIdentity.creatorIdentity)) {
            logger.logAndReportError("Member identities of a group should not contain the group creator")
        }
        val updatedMembers = (group.memberIdentities.identitiesList - myIdentity!! - group.groupIdentity.creatorIdentity).toSet()

        val oldMembers = groupModel.data?.otherMembers ?: run {
            logger.error("Group model data is null")
            return
        }

        groupModel.setMembersFromSync(updatedMembers)

        memberStateMap.forEach { (identity, state) ->
            when (state) {
                GroupSync.Update.MemberStateChange.ADDED -> {
                    when {
                        oldMembers.contains(identity) -> logger.error(
                            "Group already contains {}",
                            identity,
                        )

                        !updatedMembers.contains(identity) -> logger.error(
                            "New member set does not contain {}",
                            identity,
                        )

                        else -> {
                            globalEventBuses.groups.emit(GroupEvent.NewMember(groupModel.groupIdentity, Identity(identity)))
                        }
                    }
                }

                GroupSync.Update.MemberStateChange.LEFT, GroupSync.Update.MemberStateChange.KICKED -> {
                    when {
                        !oldMembers.contains(identity) -> logger.error(
                            "Member {} was not present in group",
                            identity,
                        )

                        updatedMembers.contains(identity) -> logger.error(
                            "Member {} still contained in group",
                            identity,
                        )

                        state == GroupSync.Update.MemberStateChange.LEFT -> {
                            globalEventBuses.groups.emit(GroupEvent.MemberLeft(groupModel.groupIdentity, Identity(identity)))
                        }

                        else -> {
                            globalEventBuses.groups.emit(GroupEvent.MemberKicked(groupModel.groupIdentity, Identity(identity)))
                        }
                    }
                }

                GroupSync.Update.MemberStateChange.UNRECOGNIZED -> logger.warn("Member state change unrecognized")
            }
        }

        if (previousGroupState != null) {
            notifyGroupStateChangeIfNeeded(groupModel, previousGroupState)
        }
    }

    private fun notifyGroupStateChangeIfNeeded(groupModel: GroupModel, previousGroupState: GroupState) {
        val newGroupState = groupModel.getGroupState() ?: return
        if (previousGroupState != newGroupState) {
            globalEventBuses.groups.emit(
                GroupEvent.GroupStateChanged(groupModel.groupIdentity, newState = newGroupState),
            )
        }
    }

    private fun applyConversationCategory(group: Group, groupModel: GroupModel) {
        if (!group.hasConversationCategory()) {
            return
        }
        val groupConversationId = GroupConversationId(groupDatabaseId = groupModel.getDatabaseId())
        when (group.conversationCategory) {
            ConversationCategory.DEFAULT -> conversationCategoryService.persistRemovePrivateMark(groupConversationId)
            ConversationCategory.PROTECTED -> conversationCategoryService.persistAddPrivateMark(groupConversationId)
            ConversationCategory.UNRECOGNIZED -> unrecognizedValue("Group.conversationCategory")
            null -> nullValue("Group.conversationCategory")
        }
    }

    private fun applyConversationVisibility(group: Group, groupModel: GroupModel) {
        val conversationVisibility = group.getConversationVisibilityOrNull()
            ?: return

        groupModel.setConversationVisibilityFromSync(conversationVisibility)
    }

    private fun removeGroupAvatar(groupModel: GroupModel) {
        if (fileService.hasGroupProfilePicture(groupModel.getDatabaseId())) {
            fileService.removeGroupProfilePicture(groupModel)
            globalEventBuses.groups.emit(GroupEvent.GroupProfilePictureUpdated(groupModel.groupIdentity))
        }
    }

    private fun Blob.loadGroupProfilePictureAndMarkAsDone(groupModel: GroupModel) {
        val blobLoadingResult = loadAndMarkAsDone(
            okHttpClient = okHttpClient,
            version = AppVersionProvider.appVersion,
            serverAddressProvider = serverAddressProvider,
            multiDevicePropertyProvider = multiDeviceManager.propertiesProvider,
            symmetricEncryptionService = symmetricEncryptionService,
            fallbackNonce = ProtocolDefines.GROUP_PHOTO_NONCE,
            downloadBlobScope = BlobScope.Local,
            markAsDoneBlobScope = BlobScope.Local,
        )
        when (blobLoadingResult) {
            is ReflectedBlobDownloader.BlobLoadingResult.Success -> {
                if (!ExifInterface.isJpegFormat(blobLoadingResult.blobBytes)) {
                    logger.warn("Received group profile picture that is not a jpeg")
                }

                fileService.writeGroupProfilePicture(groupModel, blobLoadingResult.blobBytes)
                globalEventBuses.groups.emit(GroupEvent.GroupProfilePictureUpdated(groupModel.groupIdentity))
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobMirrorNotAvailable -> {
                logger.warn("Cannot download blob because blob mirror is not available", blobLoadingResult.exception)
                throw ProtocolException("Blob mirror not available")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.DecryptionFailed -> {
                logger.error("Could not decrypt group profile picture blob", blobLoadingResult.exception)
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobNotFound -> {
                logger.error("Could not download group profile picture because the blob was not found")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobDownloadCancelled -> {
                logger.error("Could not download profile picture because the download was cancelled")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.Other -> {
                logger.error("Could not download profile picture because of an exception", blobLoadingResult.exception)
            }
        }
    }

    private fun Group.toNewGroupModelData(): GroupModelData {
        val groupNotificationTriggerPolicyOverride = if (hasNotificationTriggerPolicyOverride()) {
            notificationTriggerPolicyOverride.toDataType()
        } else {
            null
        }

        return GroupModelData(
            groupIdentity = groupIdentity.convert(),
            name = name,
            createdAt = Instant.ofEpochMilli(createdAt),
            synchronizedAt = null,
            lastUpdate = Instant.now(),
            conversationVisibility = getConversationVisibilityOrNull() ?: ConversationVisibility.NORMAL,
            groupDescription = null,
            groupDescriptionChangedAt = null,
            otherMembers = Collections.unmodifiableSet(getMembers() - groupIdentity.creatorIdentity - myIdentity),
            userState = userState.convert() ?: UserState.MEMBER,
            notificationTriggerPolicyOverride = groupNotificationTriggerPolicyOverride,
        )
    }

    private fun Group.getConversationVisibilityOrNull(): ConversationVisibility? {
        if (!hasConversationVisibility()) {
            return null
        }

        return when (conversationVisibility) {
            ProtocolsConversationVisibility.NORMAL -> ConversationVisibility.NORMAL
            ProtocolsConversationVisibility.PINNED -> ConversationVisibility.PINNED
            ProtocolsConversationVisibility.ARCHIVED -> ConversationVisibility.ARCHIVED
            ProtocolsConversationVisibility.UNRECOGNIZED -> unrecognizedValue("conversationVisibility")
            null -> nullValue("conversationVisibility")
        }
    }

    private fun Group.getMembers(): Set<String> = memberIdentities.identitiesList.toSet()

    private fun Group.UserState?.convert() = when (this) {
        Group.UserState.MEMBER -> UserState.MEMBER
        Group.UserState.LEFT -> UserState.LEFT
        Group.UserState.KICKED -> UserState.KICKED
        Group.UserState.UNRECOGNIZED -> unrecognizedValue("Group.UserState")
        null -> nullValue("Group.UserState")
    }

    private fun ch.threema.protobuf.common.GroupIdentity.convert() = GroupIdentity(
        creatorIdentity = creatorIdentity,
        groupId = groupId,
    )

    private fun unrecognizedValue(valueName: String): Nothing? {
        logger.warn("Unrecognized {}", valueName)
        return null
    }

    private fun nullValue(valueName: String): Nothing? {
        logger.warn("Value {} is null", valueName)
        return null
    }
}
