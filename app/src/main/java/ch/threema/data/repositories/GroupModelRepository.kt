package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.common.DispatcherProvider
import ch.threema.data.IdentityProvider
import ch.threema.data.ModelTypeCache
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.models.GroupModelDataFactory
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.GroupId
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@SessionScoped
class GroupModelRepository(
    // Note: Synchronize access
    private val cache: ModelTypeCache<GroupIdentity, GroupModel>,
    private val databaseBackend: DatabaseBackend,
    private val identityProvider: IdentityProvider,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskManager: TaskManager,
    private val globalEventBuses: GlobalEventBuses,
    private val globalEventFlows: GlobalEventFlows,
    dispatcherProvider: DispatcherProvider,
) {
    private val coroutineScope = CoroutineScope(dispatcherProvider.worker)
    private object GroupModelRepositoryToken : RepositoryToken

    private val myIdentity by lazy { identityProvider.getIdentityString()!! }

    init {
        coroutineScope.launch {
            globalEventFlows.groups.collect { event ->
                when (event) {
                    is GroupEvent.GroupUpdated -> onModified(event.groupIdentity)
                    is GroupEvent.NewMember -> onModified(event.groupIdentity)
                    is GroupEvent.MemberKicked -> onModified(event.groupIdentity)
                    is GroupEvent.MemberLeft -> onModified(event.groupIdentity)
                    is GroupEvent.UserLeftGroup -> onModified(event.groupIdentity)
                    is GroupEvent.GroupRenamed -> onModified(event.groupIdentity)
                    is GroupEvent.NewGroup,
                    is GroupEvent.GroupProfilePictureUpdated,
                    is GroupEvent.GroupRemoved,
                    is GroupEvent.GroupStateChanged,
                    -> Unit
                }
            }
        }
    }

    private fun onModified(groupIdentity: GroupIdentity) {
        cache.get(groupIdentity)?.refreshFromDb(GroupModelRepositoryToken)
    }

    @Synchronized
    fun getAll(): Collection<GroupModel> =
        databaseBackend.getAllGroups()
            .mapNotNull { dbGroup ->
                val groupIdentity = GroupIdentity(dbGroup.creatorIdentity, GroupId(dbGroup.groupId).toLong())
                cache.getOrCreate(groupIdentity) {
                    GroupModel(
                        groupIdentity = groupIdentity,
                        data = GroupModelDataFactory.toDataType(dbGroup),
                        databaseBackend = databaseBackend,
                        identityProvider = identityProvider,
                        multiDeviceManager = multiDeviceManager,
                        taskManager = taskManager,
                        globalEventBuses = globalEventBuses,
                    )
                }
            }

    /**
     * Get the group with the [groupDatabaseId]. Note that this call always accesses the database.
     * Use [getByGroupIdentity] or [getByCreatorIdentityAndId] to reduce database accesses.
     */
    @Synchronized
    fun getByGroupDatabaseId(groupDatabaseId: GroupDatabaseId): GroupModel? {
        // Note that we need to access the database to get the corresponding group model. The
        // fetched group is needed to get the group identity. If the group is not cached, the
        // fetched group data is used to construct the group model. Otherwise, the cached group model
        // is returned.
        val dbGroup = databaseBackend.getGroupByGroupDatabaseId(groupDatabaseId) ?: return null
        val groupIdentity = GroupIdentity(dbGroup.creatorIdentity, GroupId(dbGroup.groupId).toLong())
        return cache.getOrCreate(groupIdentity) {
            GroupModel(
                groupIdentity,
                GroupModelDataFactory.toDataType(dbGroup),
                databaseBackend,
                identityProvider = identityProvider,
                multiDeviceManager = multiDeviceManager,
                taskManager = taskManager,
                globalEventBuses = globalEventBuses,
            )
        }
    }

    @Synchronized
    fun getByCreatorIdentityAndId(creatorIdentity: IdentityString, groupId: GroupId): GroupModel? {
        val groupIdentity = GroupIdentity(creatorIdentity, groupId.toLong())
        return getByGroupIdentity(groupIdentity)
    }

    @Synchronized
    fun getByGroupIdentity(groupIdentity: GroupIdentity): GroupModel? {
        return cache.getOrCreate(groupIdentity) {
            val dbGroup = databaseBackend.getGroupByGroupIdentity(groupIdentity) ?: return@getOrCreate null
            GroupModel(
                groupIdentity,
                GroupModelDataFactory.toDataType(dbGroup),
                databaseBackend,
                identityProvider = identityProvider,
                multiDeviceManager = multiDeviceManager,
                taskManager = taskManager,
                globalEventBuses = globalEventBuses,
            )
        }
    }

    /**
     * Creates the given group. Note that this change is not reflected! The group is just persisted.
     *
     * @throws GroupStoreException if the group cannot be inserted into the database
     * @throws GroupAlreadyExistsException if the group already exists
     */
    fun createFromSync(groupModelData: GroupModelData): GroupModel {
        return persistNewGroup(groupModelData)
    }

    /**
     * Creates the given group. Note that this change is not reflected!
     *
     * @throws GroupStoreException if the group cannot be inserted into the database
     * @throws GroupAlreadyExistsException if the group already exists
     */
    fun persistNewGroup(groupModelData: GroupModelData): GroupModel {
        val groupModel = synchronized(this) {
            if (getByGroupIdentity(groupModelData.groupIdentity) != null) {
                throw GroupAlreadyExistsException()
            }
            try {
                databaseBackend.createGroup(GroupModelDataFactory.toDbType(groupModelData))
            } catch (e: SQLiteException) {
                throw GroupStoreException(e)
            }

            getByGroupIdentity(groupModelData.groupIdentity)
                ?: throw IllegalStateException("Group must exist at this point")
        }

        globalEventBuses.groups.emit(GroupEvent.NewGroup(groupModel.groupIdentity))

        if (groupModelData.isMember) {
            globalEventBuses.groups.emit(GroupEvent.NewMember(groupModel.groupIdentity, Identity(myIdentity)))
        }

        if (groupModelData.groupIdentity.creatorIdentity != myIdentity) {
            globalEventBuses.groups.emit(GroupEvent.NewMember(groupModel.groupIdentity, Identity(groupModelData.groupIdentity.creatorIdentity)))
        }

        groupModelData.otherMembers.forEach { memberIdentity ->
            globalEventBuses.groups.emit(GroupEvent.NewMember(groupModel.groupIdentity, Identity(memberIdentity)))
        }

        return groupModel
    }

    /**
     * Remove the group with the given group identity. Note that this only removes data associated
     * to the group that is present in the database. Other data belonging to the group like avatars,
     * files, or chat settings are not affected and must be deleted separately.
     */
    fun persistRemovedGroup(groupIdentity: GroupIdentity) {
        val groupModel = getByGroupIdentity(groupIdentity)
        val groupDbColumnId = groupModel?.getDatabaseId() ?: run {
            return
        }

        synchronized(this) {
            databaseBackend.removeGroup(groupDbColumnId)
            cache.remove(groupIdentity)
            groupModel.refreshFromDb(GroupModelRepositoryToken)
        }

        globalEventBuses.groups.emit(GroupEvent.GroupRemoved(groupDbColumnId))
    }

    fun destroy() {
        coroutineScope.cancel()
    }
}

/**
 * This exception is thrown if the group could not be added.
 */
sealed class GroupCreateException(msg: String, e: Exception? = null) : ThreemaException(msg, e)

/**
 * This exception is thrown if the group could not be added.
 */
class GroupStoreException(e: SQLiteException) : GroupCreateException("Failed to store the group", e)

/**
 * This exception is thrown if the group already exists.
 */
class GroupAlreadyExistsException : GroupCreateException("Group already exists")
