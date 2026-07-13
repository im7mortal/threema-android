package ch.threema.data.storage

import android.database.sqlite.SQLiteException
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.domain.types.GroupDatabaseId
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
     * Insert a new contact.
     *
     * @throws SQLiteException if insertion fails due to a conflict
     * @throws IllegalArgumentException if the length of the identity or public key is invalid
     * @throws DatabaseException if the contact could not be added due to a failing precondition,
     * e.g. because a contact with the same public key already exists
     */
    fun createContact(dbContact: DbContact)

    /**
     * Return the contact with the specified [identity].
     */
    fun getContactByIdentity(identity: IdentityString): DbContact?

    fun getContactsByIdentities(identities: Set<IdentityString>): List<DbContact>

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
     * Remove all associated data in the database of the given group identity.
     */
    fun removeGroup(localDbId: Long)

    /**
     * Get all groups.
     */
    fun getAllGroups(): Collection<DbGroup>

    /**
     * Return the group with the specified [groupDatabaseId].
     */
    fun getGroupByGroupDatabaseId(groupDatabaseId: GroupDatabaseId): DbGroup?

    /**
     * Return the group with the specified [groupIdentity].
     */
    fun getGroupByGroupIdentity(groupIdentity: GroupIdentity): DbGroup?

    /**
     * Return the row id of the group with the specified [groupIdentity].
     */
    fun getGroupDatabaseId(groupIdentity: GroupIdentity): Long?

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
}
