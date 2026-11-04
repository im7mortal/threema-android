package ch.threema.data.storage

import android.database.sqlite.SQLiteException
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.UserState
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import java.time.Instant

/**
 * This interface fully abstracts the database access.
 */
interface DatabaseBackend {
    /**
     *  Returns all contacts.
     */
    fun getAllContacts(): List<DbContact>

    /**
     * Get all contact's identities.
     */
    fun getAllContactIdentities(): Set<IdentityString>

    /**
     * Checks whether a contact with the given [identity] exists
     */
    fun contactExists(identity: IdentityString): Boolean

    /**
     * Count all existing contacts whose identities are provided.
     */
    fun countExistingContacts(identities: Set<IdentityString>): UInt

    /**
     * Insert new contacts.
     *
     * @throws SQLiteException if insertion fails due to a conflict
     * @throws IllegalArgumentException if the length of the identity or public key is invalid
     * @throws DatabaseException if the contacts could not be added due to a failing precondition,
     * e.g. because a contact with the same public key already exists
     */
    fun createContact(vararg dbContacts: DbContact)

    /**
     * Return the contact with the specified [identity].
     */
    fun getContactByIdentity(identity: IdentityString): DbContact?

    fun getContactsByIdentities(identities: Set<IdentityString>): List<DbContact>

    /**
     * Returns `true` if all given contacts exist and all match the [acquaintanceLevel].
     */
    fun allContactsExistWithAcquaintanceLevel(identities: Set<IdentityString>, acquaintanceLevel: AcquaintanceLevel): Boolean

    /**
     * Update the specified contact (using the identity as lookup key).
     *
     * Note: Some fields will not be overwritten:
     *
     * - The identity
     * - The public key
     * - The createdAt timestamp
     */
    fun updateContact(dbContact: DbContact)

    /**
     * Persists the given `workLastFullSyncAt` timestamps to the database.
     *
     * All entries are upserted (inserted or updated).
     */
    fun updateContactWorkLastFullSyncAtTimestamps(workLastFullSyncAtTimestamps: Map<IdentityString, Instant>)

    /**
     * Persists the given availability statuses to the database.
     *
     * Entries whose value is [AvailabilityStatus.None] are deleted from the database, while all
     * other entries are upserted (inserted or updated).
     *
     * No-op if the availability status feature is not supported by this build.
     */
    fun persistAvailabilityStatuses(availabilityStatuses: Map<IdentityString, AvailabilityStatus>)

    /**
     * Delete the contact with the specified identity.
     *
     * Return whether the contact was deleted (true) or wasn't found (false).
     */
    fun deleteContactByIdentity(identity: IdentityString): Boolean

    /**
     * Check whether the contact is currently part of a group. Note that only groups are considered
     * where 'deleted' is set to 0.
     */
    fun isContactInGroup(identity: IdentityString): Boolean

    /**
     * Insert a new group.
     *
     * @throws SQLiteException if insertion fails due to a conflict
     */
    fun createGroup(group: DbGroup)

    /**
     * Insert new groups.
     */
    fun createGroups(groups: Set<DbGroup>): Set<GroupIdentity>

    /**
     * Remove all associated data in the database of the given group identity.
     */
    fun removeGroup(localDbId: Long)

    /**
     * Get all groups.
     */
    fun getAllGroups(): Collection<DbGroup>

    /**
     * Get all archived groups.
     */
    fun getAllArchivedGroups(): Collection<DbGroup>

    /**
     * Get all groups of a given creator.
     */
    fun getGroupsByCreator(identity: Identity): Collection<DbGroup>

    /**
     * Return the group with the specified [groupDatabaseId].
     */
    fun getGroupByGroupDatabaseId(groupDatabaseId: GroupDatabaseId): DbGroup?

    /**
     * Return the group with the specified [groupIdentity].
     */
    fun getGroupByGroupIdentity(groupIdentity: GroupIdentity): DbGroup?

    /**
     * Return the groups with the specified [groupIdentities].
     */
    fun getGroupsByGroupIdentities(groupIdentities: Set<GroupIdentity>): Set<DbGroup>

    /**
     * Returns `true` if all the given [groupIdentities] exist.
     */
    fun allGroupsExist(groupIdentities: Set<GroupIdentity>): Boolean

    /**
     * Return the row id of the group with the specified [groupIdentity].
     */
    fun getGroupDatabaseId(groupIdentity: GroupIdentity): Long?

    /**
     * Return the database row ids for the given [groupIdentities]. The returned map might be empty of a subset of [groupIdentities].
     */
    fun getGroupDatabaseIdsByGroupIdentities(groupIdentities: Set<GroupIdentity>): Map<GroupIdentity, GroupDatabaseId>

    /**
     * Update the specified group (using the creator identity and group id as lookup key).
     *
     * Note: Some fields will not be overwritten:
     *
     * - The creator identity
     * - The group id
     * - The createdAt timestamp
     */
    fun updateGroup(group: DbGroup)

    /**
     *  Returns `true` if the group exists
     */
    fun groupExists(groupIdentity: GroupIdentity): Boolean

    /**
     * Checks if *any* of the given [groupIdentities] exist.
     */
    fun anyGroupExists(groupIdentities: Set<GroupIdentity>): Boolean

    /**
     *  Returns whether the [contactIdentity] is part of **any** group.
     *  Being part of a group means both being a member, **or** being the creator.
     *
     *  @param contactIdentity A contact's identity
     */
    fun isPartOfAnyGroup(contactIdentity: IdentityString): Boolean

    /**
     *  Returns whether the [contactIdentity] is part of **any** group that has the given [userState].
     *  Being part of a group means both being a member, **or** being the creator.
     *
     *  @param contactIdentity A contact's identity
     *  @param userState The state of the **user**
     */
    fun isPartOfAnyGroupWithUserState(contactIdentity: IdentityString, userState: UserState): Boolean

    /**
     *  Returns a set of all group identities
     */
    fun getAllGroupIdentities(): Set<GroupIdentity>
}
