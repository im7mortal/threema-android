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
import ch.threema.data.storage.DbGroup
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.UserState
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

    private val userIdentity by lazy {
        identityProvider.requireIdentityString()
    }

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
        cache[groupIdentity]?.refreshFromDb(GroupModelRepositoryToken)
    }

    @Synchronized
    fun getAll(): Collection<GroupModel> =
        databaseBackend.getAllGroups()
            .mapNotNull { dbGroup ->
                val groupIdentity = GroupIdentity(
                    creatorIdentity = dbGroup.creatorIdentity,
                    groupId = GroupId(dbGroup.groupId).toLong(),
                )
                cache.getOrCreate(groupIdentity) {
                    dbGroup.toModel()
                }
            }

    @Synchronized
    fun getAllArchived(): List<GroupModel> =
        databaseBackend.getAllArchivedGroups()
            .mapNotNull { dbGroup ->
                val groupIdentity = GroupIdentity(
                    creatorIdentity = dbGroup.creatorIdentity,
                    groupId = GroupId(dbGroup.groupId).toLong(),
                )
                cache.getOrCreate(groupIdentity) {
                    dbGroup.toModel()
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
        val dbGroup = databaseBackend.getGroupByGroupDatabaseId(groupDatabaseId)
            ?: return null
        val groupIdentity = GroupIdentity(
            creatorIdentity = dbGroup.creatorIdentity,
            groupId = GroupId(dbGroup.groupId).toLong(),
        )
        return cache.getOrCreate(groupIdentity) {
            dbGroup.toModel()
        }
    }

    @Synchronized
    fun getByCreatorIdentityAndId(creatorIdentity: IdentityString, groupId: GroupId): GroupModel? {
        val groupIdentity = GroupIdentity(creatorIdentity, groupId.toLong())
        return getByGroupIdentity(groupIdentity)
    }

    @Synchronized
    fun getByGroupIdentity(groupIdentity: GroupIdentity): GroupModel? =
        cache.getOrCreate(groupIdentity) {
            databaseBackend.getGroupByGroupIdentity(groupIdentity)?.toModel()
        }

    @Synchronized
    fun getByCreator(creatorIdentity: IdentityString): List<GroupModel> =
        databaseBackend.getGroupsByCreator(Identity(creatorIdentity))
            .mapNotNull { dbGroup ->
                val groupIdentity = GroupIdentity(creatorIdentity, GroupId(dbGroup.groupId).toLong())
                cache.getOrCreate(groupIdentity) {
                    dbGroup.toModel()
                }
            }

    @Synchronized
    fun exists(groupIdentity: GroupIdentity): Boolean =
        cache.exists(groupIdentity) || databaseBackend.groupExists(groupIdentity)

    /**
     * Creates the given group. Note that this change is not reflected! The group is just persisted.
     *
     * @throws GroupStoreException if the group cannot be inserted into the database
     * @throws GroupAlreadyExistsException if the group already exists
     */
    @Throws(GroupStoreException::class, GroupAlreadyExistsException::class)
    fun createFromSync(groupModelData: GroupModelData): GroupModel =
        persistNewGroup(groupModelData)

    /**
     * Persist the given [groups].
     *
     * Note that this change is *not* reflected!
     *
     * @throws GroupAlreadyExistsException if *any* group already exists
     * @throws GroupStoreException if a group cannot be inserted into the database
     */
    fun persistNewGroups(groups: Set<GroupModelData>): List<GroupModel> {
        if (groups.isEmpty()) {
            return emptyList()
        }
        val groupModels = synchronized(this) {
            val groupIdentities = groups.map(GroupModelData::groupIdentity).toSet()
            if (databaseBackend.anyGroupExists(groupIdentities)) {
                throw GroupAlreadyExistsException()
            }
            val newGroupIdentities = try {
                databaseBackend.createGroups(
                    groups = groups.map(GroupModelDataFactory::toDbType).toSet(),
                )
            } catch (e: SQLiteException) {
                throw GroupStoreException("Failed to persist groups", e)
            }

            return@synchronized getAllByGroupIdentities(newGroupIdentities)
        }
        // Emit all group events
        with(globalEventBuses.groups) {
            groupModels.forEach { groupModel ->
                emit(groupModel.eventNew())
                if (groupModel.isMember()) {
                    emit(groupModel.eventNewMemberUser(userIdentity))
                }
                if (!groupModel.isCreator()) {
                    emit(groupModel.eventNewMemberCreator())
                }
                groupModel.data?.otherMembers?.forEach { memberIdentity ->
                    emit(groupModel.eventNewMemberContact(memberIdentity))
                }
            }
        }
        return groupModels
    }

    /**
     * Get all *existing* group models by [groupIdentities].
     * The result might be empty or a subset of the requested groups.
     *
     * The result list guarantees to be in the same order as [groupIdentities].
     */
    @Synchronized
    fun getAllByGroupIdentities(groupIdentities: Set<GroupIdentity>): List<GroupModel> {
        if (groupIdentities.isEmpty()) {
            return emptyList()
        }
        // Store results in a map to preserve the input order
        val resultsMap: MutableMap<GroupIdentity, GroupModel?> = groupIdentities.associateWithTo(mutableMapOf()) { null }
        val cacheMisses = mutableSetOf<GroupIdentity>()
        // Try to find all in cache
        for (groupIdentity in groupIdentities) {
            val cachedGroupModel: GroupModel? = cache.get(groupIdentity)
            if (cachedGroupModel != null) {
                resultsMap[groupIdentity] = cachedGroupModel
            } else {
                cacheMisses.add(groupIdentity)
            }
        }
        // Happy case: all models found in cache
        if (cacheMisses.isEmpty()) {
            return resultsMap.values.filterNotNull()
        }
        // Search all missing models in database and add found models to the cache
        databaseBackend
            .getGroupsByGroupIdentities(cacheMisses)
            .map { dbGroup -> dbGroup.toModel() }
            .forEach { groupModel ->
                cache.putIfAbsent(groupModel.groupIdentity, groupModel)
                resultsMap[groupModel.groupIdentity] = groupModel
            }
        return resultsMap.values.filterNotNull()
    }

    /**
     * Returns `true` if all given [groupIdentities] exist.
     */
    @Synchronized
    fun allExist(groupIdentities: Set<GroupIdentity>): Boolean {
        if (groupIdentities.isEmpty()) {
            return true
        }
        // Try to find all in cache
        val cacheMisses = buildSet {
            for (groupIdentity in groupIdentities) {
                if (!cache.exists(groupIdentity)) {
                    add(groupIdentity)
                }
            }
        }
        // Happy case: all group identities found in cache
        if (cacheMisses.isEmpty()) {
            return true
        }
        // Check remaining group identities in database
        return databaseBackend.allGroupsExist(
            groupIdentities = cacheMisses,
        )
    }

    /**
     * Persist the given group.
     *
     * Note that this is *not* reflected!
     *
     * @throws GroupAlreadyExistsException if the group already exists
     * @throws GroupStoreException if the group cannot be inserted into the database
     * @throws IllegalStateException if an internal error occurred
     */
    @Throws(GroupStoreException::class, GroupAlreadyExistsException::class)
    fun persistNewGroup(groupModelData: GroupModelData): GroupModel {
        val groupModel = synchronized(this) {
            if (getByGroupIdentity(groupModelData.groupIdentity) != null) {
                throw GroupAlreadyExistsException()
            }
            try {
                databaseBackend.createGroup(GroupModelDataFactory.toDbType(groupModelData))
            } catch (e: SQLiteException) {
                throw GroupStoreException("Failed to store the group", e)
            }

            getByGroupIdentity(groupModelData.groupIdentity)
                ?: throw IllegalStateException("Group must exist at this point")
        }
        // Emit all group events
        with(globalEventBuses.groups) {
            emit(groupModel.eventNew())
            if (groupModel.isMember()) {
                emit(groupModel.eventNewMemberUser(userIdentity))
            }
            if (!groupModel.isCreator()) {
                emit(groupModel.eventNewMemberCreator())
            }
            groupModelData.otherMembers.forEach { memberIdentity ->
                emit(groupModel.eventNewMemberContact(memberIdentity))
            }
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

    /**
     * Checks if *any* of the given [groupIdentities] exist.
     */
    fun existsAnyByGroupIdentity(groupIdentities: Set<GroupIdentity>): Boolean {
        if (groupIdentities.isEmpty()) {
            return false
        }
        synchronized(this) {
            if (!cache.isEmpty()) {
                for (groupIdentity in groupIdentities) {
                    if (cache.exists(groupIdentity)) {
                        return true
                    }
                }
            }
        }
        return databaseBackend.anyGroupExists(groupIdentities)
    }

    /**
     *  Returns whether the [contactIdentity] is part of **any** group.
     *  Being part of a group means both being a member, **or** being the creator.
     *
     *  @param contactIdentity A contact's identity
     *
     *  @throws IllegalArgumentException In case [contactIdentity] is the user's identity
     */
    fun isPartOfAnyGroup(contactIdentity: IdentityString): Boolean {
        require(contactIdentity != userIdentity) {
            "Given identity must not be the user's identity"
        }
        val isPartOfAnyGroupFromCache = synchronized(this) {
            cache.values().any { groupModel ->
                groupModel.data?.otherMembersAndCreator?.contains(contactIdentity) == true
            }
        }
        if (isPartOfAnyGroupFromCache) {
            return true
        }
        return databaseBackend.isPartOfAnyGroup(contactIdentity)
    }

    /**
     *  Returns whether the [contactIdentity] is part of **any** group that has the given [userState].
     *  Being part of a group means both being a member, **or** being the creator.
     *
     *  @param contactIdentity A contact's identity
     *  @param userState The state of the **user**
     *
     *  @throws IllegalArgumentException In case [contactIdentity] is the user's identity
     */
    fun isPartOfAnyGroupWithUserState(contactIdentity: IdentityString, userState: UserState): Boolean {
        require(contactIdentity != userIdentity) {
            "Given identity must not be the user's identity"
        }
        val isPartOfAnyGroupFromCache = synchronized(this) {
            cache.values().any { groupModel ->
                groupModel.data?.otherMembersAndCreator?.contains(contactIdentity) == true &&
                    groupModel.data?.userState == userState
            }
        }
        if (isPartOfAnyGroupFromCache) {
            return true
        }
        return databaseBackend.isPartOfAnyGroupWithUserState(contactIdentity, userState)
    }

    fun getAllGroupIdentities(): Set<GroupIdentity> = databaseBackend.getAllGroupIdentities()

    /**
     * Return the database row ids for the given [groupIdentities]. The returned map might be empty of a subset of [groupIdentities].
     */
    fun getGroupDatabaseIdsByGroupIdentities(groupIdentities: Set<GroupIdentity>): Map<GroupIdentity, GroupDatabaseId> =
        if (!groupIdentities.isEmpty()) {
            databaseBackend.getGroupDatabaseIdsByGroupIdentities(groupIdentities)
        } else {
            emptyMap()
        }

    fun destroy() {
        coroutineScope.cancel()
    }

    private fun DbGroup.toModel(): GroupModel =
        GroupModel(
            groupIdentity = GroupIdentity(
                creatorIdentity = creatorIdentity,
                groupId = GroupId(groupId).toLong(),
            ),
            data = GroupModelDataFactory.toDataType(this),
            databaseBackend = databaseBackend,
            identityProvider = identityProvider,
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
            globalEventBuses = globalEventBuses,
        )

    private fun GroupModel.eventNew() = GroupEvent.NewGroup(groupIdentity)

    private fun GroupModel.eventNewMemberUser(userIdentity: IdentityString) = GroupEvent.NewMember(
        groupIdentity = groupIdentity,
        identity = Identity(userIdentity),
    )

    private fun GroupModel.eventNewMemberCreator() = GroupEvent.NewMember(
        groupIdentity = groupIdentity,
        identity = Identity(groupIdentity.creatorIdentity),
    )

    private fun GroupModel.eventNewMemberContact(identity: IdentityString) = GroupEvent.NewMember(
        groupIdentity = groupIdentity,
        identity = Identity(identity),
    )
}

/**
 * This exception is thrown if the group could not be added.
 */
sealed class GroupCreateException(msg: String, cause: Exception? = null) : ThreemaException(msg, cause)

/**
 * This exception is thrown if the group could not be added.
 */
class GroupStoreException(msg: String, cause: SQLiteException) : GroupCreateException(msg, cause)

/**
 * This exception is thrown if the group already exists.
 */
class GroupAlreadyExistsException : GroupCreateException("Group already exists")
