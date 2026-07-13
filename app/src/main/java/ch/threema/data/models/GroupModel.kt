package ch.threema.data.models

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.GroupService
import ch.threema.app.tasks.ReflectGroupSyncUpdateTask
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupState
import ch.threema.data.repositories.RepositoryToken
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.UserState
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow

private val logger = getThreemaLogger("data.GroupModel")

/**
 * A group.
 */
class GroupModel(
    val groupIdentity: GroupIdentity,
    data: GroupModelData,
    private val databaseBackend: DatabaseBackend,
    private val identityProvider: IdentityProvider,
    multiDeviceManager: MultiDeviceManager,
    taskManager: TaskManager,
    private val globalEventBuses: GlobalEventBuses,
) : BaseModel<GroupModelData, ReflectGroupSyncUpdateTask>(
    modelName = "GroupModel",
    mutableData = MutableStateFlow(data),
    multiDeviceManager = multiDeviceManager,
    taskManager = taskManager,
) {
    private val databaseId: Long? by lazy { databaseBackend.getGroupDatabaseId(groupIdentity) }

    private val myIdentity by lazy { identityProvider.getIdentityString()!! }

    /**
     *  We have to make the bridge over to the old GroupService in order
     *  to keep the new and old caches both correct.
     */
    private val deprecatedGroupService: GroupService? by lazy {
        val serviceManager: ServiceManager? = ServiceManager.get()
        if (serviceManager == null) {
            logger.warn("Tried to get the groupService before the service-manager was created.")
        }
        serviceManager?.groupService
    }

    init {
        require(groupIdentity == data.groupIdentity) {
            "Group identity mismatch"
        }
    }

    /**
     * Get the database id of the group.
     */
    fun getDatabaseId(): GroupDatabaseId =
        databaseId ?: error("Database id of group is null")

    /**
     * Gets the group state. If the group does not exist anymore, null is returned.
     */
    fun getGroupState(): GroupState? {
        val groupModelData = data ?: return null
        return if (groupIdentity.creatorIdentity == myIdentity && groupModelData.otherMembers.isEmpty()) {
            GroupState.NOTES
        } else {
            GroupState.PEOPLE
        }
    }

    /**
     * Checks whether the group is a notes group or not. If the group does not exist anymore, null
     * is returned.
     */
    fun isNotesGroup(): Boolean? =
        when (getGroupState()) {
            GroupState.NOTES -> true
            GroupState.PEOPLE -> false
            null -> null
        }

    /**
     * Checks whether the user is the creator of the group or not.
     */
    fun isCreator(): Boolean =
        groupIdentity.creatorIdentity == myIdentity

    /**
     * Checks whether the user has been kicked from the group or not.
     */
    fun isKicked(): Boolean =
        data?.userState == UserState.KICKED

    /**
     * Checks whether the user is a member of the group or not. The user is also considered a member if the user is the creator and hasn't disbanded
     * the group.
     *
     * Note that a reason for not being a member may be that the group no longer exists.
     */
    fun isMember(): Boolean =
        data?.isMember == true

    /**
     * Checks whether the group can be left or not. Note that if the group has been deleted, this method returns false.
     *
     * A group can be left if the user is *not* the creator and still a member.
     */
    fun isLeavable(): Boolean =
        !isCreator() && isMember()

    /**
     * Checks whether the group can be disbanded or not. Note that if the group has been deleted, this method returns false.
     *
     * A group can be disbanded if the user is the creator and still a member and there is at least one other member.
     */
    fun isDisbandable(): Boolean =
        isCreator() && isMember() && data?.otherMembers?.isNotEmpty() ?: false

    /**
     * Get the set of identities that should get the messages of the user. This includes all members as well as the creator. Note that the user is
     * never included in the set - even if they are the creator.
     */
    fun getRecipients(): Set<IdentityString> {
        val groupModelData = data ?: return emptySet()
        return if (isCreator()) {
            groupModelData.otherMembers
        } else {
            groupModelData.otherMembers + groupIdentity.creatorIdentity
        }
    }

    /**
     * Set new group name from sync.
     */
    fun setNameFromSync(newName: String) {
        updateFields(
            methodName = "setNameFromSync",
            detectChanges = { originalData -> originalData.name != newName },
            updateData = { originalData -> originalData.copy(name = newName) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                globalEventBuses.groups.emit(GroupEvent.GroupRenamed(groupIdentity, newName))
            },
        )
    }

    /**
     * Persist the group name. Note that this change is not reflected and must therefore only be used
     * in cases where the reflection already is done.
     */
    fun persistName(newName: String) {
        updateFields(
            methodName = "persistName",
            detectChanges = { originalData -> originalData.name != newName },
            updateData = { originalData -> originalData.copy(name = newName) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                globalEventBuses.groups.emit(GroupEvent.GroupRenamed(groupIdentity, newName))
            },
        )
    }

    /**
     * Set the members from sync. Note that this does not trigger the event bus.
     */
    fun setMembersFromSync(members: Set<IdentityString>) {
        updateFields(
            methodName = "setMembersFromSync",
            detectChanges = { originalData -> originalData.otherMembers != members },
            updateData = { originalData ->
                originalData.copy(
                    otherMembers = Collections.unmodifiableSet(members),
                )
            },
            updateDatabase = ::updateDatabase,
            onUpdated = { },
        )
    }

    /**
     * Persist changes of the group members. Note that this change is not reflected and must
     * therefore only be used in cases where the reflection already is done.
     */
    fun persistMemberChanges(addedMembers: Set<IdentityString>, removedMembers: Set<IdentityString>) {
        val data = ensureNotDeleted("persistMemberChanges")
        val newMemberSet = data.otherMembers.minus(removedMembers).plus(addedMembers)
        val previousGroupState = getGroupState()!!

        updateFields(
            methodName = "persistMemberChanges",
            detectChanges = { originalData -> originalData.otherMembers != newMemberSet },
            updateData = { originalData -> originalData.copy(otherMembers = newMemberSet) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                addedMembers.forEach { memberIdentity ->
                    globalEventBuses.groups.emit(GroupEvent.NewMember(groupIdentity, Identity(memberIdentity)))
                }
                removedMembers.forEach { kickedIdentity ->
                    globalEventBuses.groups.emit(GroupEvent.MemberKicked(groupIdentity, Identity(kickedIdentity)))
                }
                notifyGroupStateChangeIfNeeded(previousGroupState)
            },
        )
    }

    private fun notifyGroupStateChangeIfNeeded(previousGroupState: GroupState) {
        val newGroupState = getGroupState() ?: return
        if (previousGroupState != newGroupState) {
            globalEventBuses.groups.emit(
                GroupEvent.GroupStateChanged(groupIdentity, newState = newGroupState),
            )
        }
    }

    /**
     * Set the user state from sync.
     */
    fun setUserStateFromSync(userState: UserState) {
        persistUserState(userState)
    }

    /**
     * Persist the group user state. Note that this change is not reflected and must therefore only
     * be used in cases where the reflection already is done.
     */
    fun persistUserState(userState: UserState) {
        updateFields(
            methodName = "persistUserState",
            detectChanges = { originalData -> originalData.userState != userState },
            updateData = { originalData -> originalData.copy(userState = userState) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                when (userState) {
                    UserState.MEMBER -> {
                        globalEventBuses.groups.emit(GroupEvent.NewMember(groupIdentity, Identity(myIdentity)))
                    }
                    UserState.LEFT -> {
                        globalEventBuses.groups.emit(GroupEvent.MemberLeft(groupIdentity, Identity(myIdentity)))
                        globalEventBuses.groups.emit(GroupEvent.UserLeftGroup(groupIdentity))
                    }
                    UserState.KICKED -> {
                        globalEventBuses.groups.emit(GroupEvent.MemberKicked(groupIdentity, Identity(myIdentity)))
                    }
                }
            },
        )
    }

    /**
     * Remove a member from remote. This will update the database and trigger the corresponding
     * event bus.
     */
    @Synchronized
    fun removeLeftMemberFromRemote(memberIdentity: IdentityString) {
        val data = ensureNotDeleted("removeLeftMemberFromRemote")
        val previousMembers = data.otherMembers
        val newMembers = previousMembers.filter { it != memberIdentity }.toSet()
        val previousGroupState = getGroupState() ?: return

        updateFields(
            methodName = "removeLeftMemberFromRemote",
            detectChanges = { originalData -> originalData.otherMembers != newMembers },
            updateData = { originalData -> originalData.copy(otherMembers = newMembers) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                globalEventBuses.groups.emit(GroupEvent.MemberLeft(groupIdentity, Identity(memberIdentity)))
                notifyGroupStateChangeIfNeeded(previousGroupState)
            },
        )
    }

    /**
     * Update the group's notification-trigger-policy-override.
     *
     * @throws [ModelDeletedException] if model is deleted.
     *
     * @see GroupNotificationTriggerPolicyOverride
     */
    fun setNotificationTriggerPolicyOverrideFromSync(notificationTriggerPolicyOverride: GroupNotificationTriggerPolicyOverride?) {
        updateFields(
            methodName = "setNotificationTriggerPolicyOverrideFromSync",
            detectChanges = { originalData -> originalData.notificationTriggerPolicyOverride != notificationTriggerPolicyOverride },
            updateData = { originalData -> originalData.copy(notificationTriggerPolicyOverride = notificationTriggerPolicyOverride) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                deprecatedGroupService?.removeFromCache(groupIdentity)
                globalEventBuses.groups.emit(GroupEvent.GroupUpdated(groupIdentity))
            },
        )
    }

    /**
     * Update the group's notification-trigger-policy-override and reflecting the change.
     *
     * @throws [ModelDeletedException] if model is deleted.
     *
     * @see GroupNotificationTriggerPolicyOverride
     */
    fun setNotificationTriggerPolicyOverrideFromLocal(notificationTriggerPolicyOverride: GroupNotificationTriggerPolicyOverride?) {
        updateFields(
            methodName = "setNotificationTriggerPolicyOverrideFromLocal",
            detectChanges = { originalData -> originalData.notificationTriggerPolicyOverride != notificationTriggerPolicyOverride },
            updateData = { originalData -> originalData.copy(notificationTriggerPolicyOverride = notificationTriggerPolicyOverride) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                deprecatedGroupService?.removeFromCache(groupIdentity)
                globalEventBuses.groups.emit(GroupEvent.GroupUpdated(groupIdentity))
            },
            reflectUpdateTask = ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate(
                newNotificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
                groupIdentity = groupIdentity,
            ),
        )
    }

    /**
     * Set the conversation visibility.
     */
    fun setConversationVisibilityFromLocalOrRemote(conversationVisibility: ConversationVisibility) {
        this.updateFields(
            methodName = "setConversationVisibilityFromLocalOrRemote",
            detectChanges = { originalData -> originalData.conversationVisibility != conversationVisibility },
            updateData = { originalData -> originalData.copy(conversationVisibility = conversationVisibility) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                deprecatedGroupService?.removeFromCache(groupIdentity)
                globalEventBuses.groups.emit(GroupEvent.GroupUpdated(groupIdentity))
            },
            reflectUpdateTask = ReflectGroupSyncUpdateTask.ReflectGroupConversationVisibilityUpdate(
                conversationVisibility = conversationVisibility,
                groupIdentity = groupIdentity,
            ),
        )
    }

    /**
     * Set the conversation visibility from sync.
     */
    fun setConversationVisibilityFromSync(conversationVisibility: ConversationVisibility) {
        this.updateFields(
            methodName = "setConversationVisibilityFromSync",
            detectChanges = { originalData -> originalData.conversationVisibility != conversationVisibility },
            updateData = { originalData -> originalData.copy(conversationVisibility = conversationVisibility) },
            updateDatabase = ::updateDatabase,
            onUpdated = {
                deprecatedGroupService?.removeFromCache(groupIdentity)
                globalEventBuses.groups.emit(GroupEvent.GroupUpdated(groupIdentity))
            },
        )
    }

    private fun updateDatabase(updatedData: GroupModelData) {
        databaseBackend.updateGroup(GroupModelDataFactory.toDbType(updatedData))
    }

    /**
     * Update all data from database.
     *
     * Note: This method may only be called by the repository, in code that bridges the old models
     * to the new models. All other code does not need to refresh the data, the model's state flow
     * should always be up to date.
     *
     * Note: If the model is marked as deleted, then this will have no effect.
     */
    internal fun refreshFromDb(@Suppress("UNUSED_PARAMETER") token: RepositoryToken) {
        logger.info("Refresh from database")
        synchronized(this) {
            if (mutableData.value == null) {
                logger.warn("Cannot refresh deleted ${this.modelName} from DB")
                return
            }
            val dbGroup = databaseBackend.getGroupByGroupIdentity(groupIdentity) ?: run {
                mutableData.value = null
                return
            }
            val newData = GroupModelDataFactory.toDataType(dbGroup)
            check(newData.groupIdentity == groupIdentity) {
                "Cannot update group model with data for different group: ${newData.groupIdentity} != $groupIdentity"
            }
            mutableData.value = newData
        }
    }
}
