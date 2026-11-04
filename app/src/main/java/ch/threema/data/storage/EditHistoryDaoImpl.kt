package ch.threema.data.storage

import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.core.database.getStringOrNull
import androidx.sqlite.db.transaction
import ch.threema.android.map
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DatabaseHelpers.makePlaceholders
import ch.threema.data.datatypes.EditHistoryType
import ch.threema.data.storage.SqliteDatabaseBackend.Companion.MAX_VARIABLE_NUMBER
import ch.threema.domain.types.MessageUid
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.bindStringOrNull
import ch.threema.storage.buildContentValues
import ch.threema.storage.factories.ContactEditHistoryEntryModelFactory
import ch.threema.storage.factories.GroupEditHistoryEntryModelFactory
import ch.threema.storage.runQuery
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("EditHistoryDaoImpl")

class EditHistoryDaoImpl(
    private val databaseProvider: DatabaseProvider,
) : EditHistoryDao {

    override fun create(
        dbEditHistoryEntry: DbEditHistoryEntry,
        editHistoryType: EditHistoryType,
    ): DbEditHistoryEntry {
        val contentValues = buildContentValues {
            put(DbEditHistoryEntry.COLUMN_MESSAGE_UID, dbEditHistoryEntry.messageUid)
            put(DbEditHistoryEntry.COLUMN_MESSAGE_ID, dbEditHistoryEntry.messageId)
            put(DbEditHistoryEntry.COLUMN_TEXT, dbEditHistoryEntry.text)
            put(DbEditHistoryEntry.COLUMN_CREATED_AT, dbEditHistoryEntry.createdAt.toEpochMilli())
        }
        val table = when (editHistoryType) {
            EditHistoryType.CONTACT -> ContactEditHistoryEntryModelFactory.TABLE
            EditHistoryType.GROUP -> GroupEditHistoryEntryModelFactory.TABLE
        }
        val uid = databaseProvider.writableDatabase.insert(
            table = table,
            conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
            values = contentValues,
        )
        if (uid < 0) {
            throw SQLiteException("Failed to insert entry")
        }
        return dbEditHistoryEntry.copy(
            uid = uid.toInt(),
        )
    }

    override fun createAll(
        editHistoryEntriesContactMessages: List<DbEditHistoryEntry>,
        editHistoryEntriesGroupMessages: List<DbEditHistoryEntry>,
    ): List<DbEditHistoryEntry> {
        if (editHistoryEntriesContactMessages.isEmpty() && editHistoryEntriesGroupMessages.isEmpty()) {
            return emptyList()
        }
        val insertContactEditHistoryStatement = databaseProvider.writableDatabase.compileStatement(
            """
                INSERT INTO ${ContactEditHistoryEntryModelFactory.TABLE} (
                ${DbEditHistoryEntry.COLUMN_MESSAGE_UID},
                ${DbEditHistoryEntry.COLUMN_MESSAGE_ID},
                ${DbEditHistoryEntry.COLUMN_TEXT},
                ${DbEditHistoryEntry.COLUMN_CREATED_AT}
                ) VALUES (${makePlaceholders(4)})
            """.trimIndent(),
        )
        val insertGroupEditHistoryStatement = databaseProvider.writableDatabase.compileStatement(
            """
                INSERT INTO ${GroupEditHistoryEntryModelFactory.TABLE} (
                ${DbEditHistoryEntry.COLUMN_MESSAGE_UID},
                ${DbEditHistoryEntry.COLUMN_MESSAGE_ID},
                ${DbEditHistoryEntry.COLUMN_TEXT},
                ${DbEditHistoryEntry.COLUMN_CREATED_AT}
                ) VALUES (${makePlaceholders(4)})
            """.trimIndent(),
        )
        try {
            val results = mutableListOf<DbEditHistoryEntry>()
            databaseProvider.writableDatabase.transaction {
                for (contactEntry in editHistoryEntriesContactMessages) {
                    with(insertContactEditHistoryStatement) {
                        clearBindings()
                        bindString(1, contactEntry.messageUid)
                        bindLong(2, contactEntry.messageId.toLong())
                        bindStringOrNull(3, contactEntry.text)
                        bindLong(4, contactEntry.createdAt.toEpochMilli())
                        executeInsert()
                            .also { rowId ->
                                if (rowId >= 0) {
                                    results.add(
                                        contactEntry.copy(
                                            uid = rowId.toInt(),
                                        ),
                                    )
                                } else {
                                    throw SQLiteException("Insert contact message edit entry failed")
                                }
                            }
                    }
                }
                for (groupEntry in editHistoryEntriesGroupMessages) {
                    with(insertGroupEditHistoryStatement) {
                        clearBindings()
                        bindString(1, groupEntry.messageUid)
                        bindLong(2, groupEntry.messageId.toLong())
                        bindStringOrNull(3, groupEntry.text)
                        bindLong(4, groupEntry.createdAt.toEpochMilli())
                        executeInsert()
                            .also { rowId ->
                                if (rowId >= 0) {
                                    results.add(
                                        groupEntry.copy(
                                            uid = rowId.toInt(),
                                        ),
                                    )
                                } else {
                                    throw SQLiteException("Insert group message edit entry failed")
                                }
                            }
                    }
                }
            }
            return results
        } finally {
            insertContactEditHistoryStatement.close()
            insertGroupEditHistoryStatement.close()
        }
    }

    override fun deleteAllByMessageUid(messageUid: MessageUid) {
        var deletedEntries = databaseProvider.writableDatabase.delete(
            table = ContactEditHistoryEntryModelFactory.TABLE,
            whereClause = "${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ?",
            whereArgs = arrayOf(messageUid),
        )
        deletedEntries += databaseProvider.writableDatabase.delete(
            table = GroupEditHistoryEntryModelFactory.TABLE,
            whereClause = "${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ?",
            whereArgs = arrayOf(messageUid),
        )
        logger.debug("{} edit history entries deleted for message {}", deletedEntries, messageUid)
    }

    override fun findAllByMessageUid(messageUid: MessageUid): List<DbEditHistoryEntry> =
        databaseProvider.readableDatabase
            .rawQuery(
                "SELECT * FROM ${ContactEditHistoryEntryModelFactory.TABLE} WHERE ${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ? " +
                    "UNION " +
                    "SELECT * FROM ${GroupEditHistoryEntryModelFactory.TABLE} WHERE ${DbEditHistoryEntry.COLUMN_MESSAGE_UID} = ? " +
                    "ORDER BY ${DbEditHistoryEntry.COLUMN_UID} DESC",
                messageUid,
                messageUid,
            )
            .map(::convert)

    override fun findAllByMessageUids(messageUids: Iterable<MessageUid>): Map<MessageUid, List<DbEditHistoryEntry>> =
        arrayOf(ContactEditHistoryEntryModelFactory.TABLE, GroupEditHistoryEntryModelFactory.TABLE)
            .flatMap { table ->
                messageUids
                    .chunked(MAX_VARIABLE_NUMBER) { messageUidsChunk ->
                        val args = messageUidsChunk.toTypedArray()
                        databaseProvider.readableDatabase
                            .runQuery(
                                table = table,
                                selection = "${DbEditHistoryEntry.COLUMN_MESSAGE_UID} IN (${makePlaceholders(args.size)})",
                                selectionArgs = args,
                                orderBy = "${DbEditHistoryEntry.COLUMN_UID} DESC",
                            )
                            .map(::convert)
                    }
                    .flatten()
            }
            .groupBy { it.messageUid }

    private fun convert(cursor: Cursor): DbEditHistoryEntry =
        DbEditHistoryEntry(
            uid = cursor.getInt(getColumnIndexOrThrow(cursor, DbEditHistoryEntry.COLUMN_UID)),
            messageUid = cursor.getString(getColumnIndexOrThrow(cursor, DbEditHistoryEntry.COLUMN_MESSAGE_UID)),
            messageId = cursor.getInt(getColumnIndexOrThrow(cursor, DbEditHistoryEntry.COLUMN_MESSAGE_ID)),
            text = cursor.getStringOrNull(getColumnIndexOrThrow(cursor, DbEditHistoryEntry.COLUMN_TEXT)),
            createdAt = cursor.getInstant(getColumnIndexOrThrow(cursor, DbEditHistoryEntry.COLUMN_CREATED_AT)),
        )
}
