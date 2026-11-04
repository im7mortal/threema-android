package ch.threema.data.storage

import android.database.sqlite.SQLiteException
import ch.threema.data.datatypes.EditHistoryType
import ch.threema.domain.types.MessageUid

interface EditHistoryDao {

    /**
     * Insert a new edit history entry
     *
     * @param dbEditHistoryEntry The entry to add for the edit history
     * @param editHistoryType The edit history type that is used to select the table where the entry should be inserted
     *
     * @return The inserted [DbEditHistoryEntry]
     *
     * @throws SQLiteException in case of any database failure
     */
    fun create(dbEditHistoryEntry: DbEditHistoryEntry, editHistoryType: EditHistoryType): DbEditHistoryEntry

    /**
     * Insert multiple new edit history entries.
     * The entries must be sorted in chronologically ascending order, i.e. the oldest first.
     * Note that sorting must *not* be done based on [DbEditHistoryEntry.createdAt], as this information may come from another user
     * and might have been tampered with.
     *
     * @return The inserted [DbEditHistoryEntry] items
     *
     * @throws SQLiteException in case of any database failure
     */
    fun createAll(
        editHistoryEntriesContactMessages: List<DbEditHistoryEntry>,
        editHistoryEntriesGroupMessages: List<DbEditHistoryEntry>,
    ): List<DbEditHistoryEntry>

    /**
     * Delete all edit history entries referencing the specified message id
     */
    fun deleteAllByMessageUid(messageUid: MessageUid)

    /**
     * Find all edit history entries referencing the specified message uid
     */
    fun findAllByMessageUid(messageUid: MessageUid): List<DbEditHistoryEntry>

    /**
     * Find all edit history entries referencing the specified message uids.
     */
    fun findAllByMessageUids(messageUids: Iterable<MessageUid>): Map<MessageUid, List<DbEditHistoryEntry>>
}
