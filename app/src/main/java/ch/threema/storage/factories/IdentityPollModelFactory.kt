package ch.threema.storage.factories

import android.database.Cursor
import ch.threema.android.firstOrNull
import ch.threema.android.iterate
import ch.threema.common.DatabaseHelpers.convertToSelectionArgs
import ch.threema.common.DatabaseHelpers.makePlaceholders
import ch.threema.data.datatypes.LocalPollId
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.types.IdentityString
import ch.threema.storage.CursorHelper
import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.buildContentValues
import ch.threema.storage.getNonNullString
import ch.threema.storage.models.poll.IdentityPollModel
import ch.threema.storage.models.poll.PollModel
import ch.threema.storage.runDelete
import ch.threema.storage.runQuery

class IdentityPollModelFactory(
    databaseProvider: DatabaseProvider,
) : ModelFactory(databaseProvider, IdentityPollModel.TABLE) {

    fun getByIdentityAndPollId(identity: IdentityString, pollId: LocalPollId): IdentityPollModel? =
        getFirst(
            selection = "${IdentityPollModel.COLUMN_IDENTITY}=? AND ${IdentityPollModel.COLUMN_POLL_ID}=?",
            selectionArgs = arrayOf(identity, pollId.toString()),
        )

    fun getByPollId(pollId: LocalPollId): IdentityPollModel? =
        getFirst(
            selection = "${IdentityPollModel.COLUMN_POLL_ID}=?",
            selectionArgs = arrayOf(pollId.toString()),
        )

    private fun getFirst(selection: String, selectionArgs: Array<String>): IdentityPollModel? =
        readableDatabase.runQuery(
            table = tableName,
            selection = selection,
            selectionArgs = selectionArgs,
        )
            .firstOrNull(::convert)

    fun getIdentitiesByPollIds(pollIds: Set<LocalPollId>): Map<LocalPollId, IdentityString> = buildMap {
        pollIds
            .chunked(SqliteDatabaseBackend.MAX_VARIABLE_NUMBER)
            .forEach { pollIdsChunk ->
                readableDatabase.runQuery(
                    table = tableName,
                    columns = arrayOf(IdentityPollModel.COLUMN_POLL_ID, IdentityPollModel.COLUMN_IDENTITY),
                    selection = "${IdentityPollModel.COLUMN_POLL_ID} IN (${makePlaceholders(pollIdsChunk.size)})",
                    selectionArgs = convertToSelectionArgs(pollIdsChunk),
                )
                    .iterate { cursor ->
                        val pollId = cursor.getInt(cursor.getColumnIndexOrThrow(IdentityPollModel.COLUMN_POLL_ID))
                        val identity = cursor.getString(cursor.getColumnIndexOrThrow(IdentityPollModel.COLUMN_IDENTITY))
                        put(pollId, identity)
                    }
            }
    }

    private fun convert(cursor: Cursor): IdentityPollModel {
        val cursorHelper = CursorHelper(cursor, columnIndexCache)
        return IdentityPollModel(
            id = cursorHelper.getInt(IdentityPollModel.COLUMN_ID)!!,
            pollId = cursorHelper.getInt(IdentityPollModel.COLUMN_POLL_ID)!!,
            identity = cursorHelper.getNonNullString(IdentityPollModel.COLUMN_IDENTITY),
        )
    }

    fun exists(apiPollId: String, identity: IdentityString): Boolean {
        val query = "SELECT EXISTS(SELECT 1 FROM $tableName AS i, ${PollModel.TABLE} AS p " +
            "WHERE i.${IdentityPollModel.COLUMN_POLL_ID}=p.${PollModel.COLUMN_ID} AND " +
            "i.${IdentityPollModel.COLUMN_IDENTITY}=? AND p.${PollModel.COLUMN_API_POLL_ID}=? LIMIT 1)"
        val args = arrayOf(identity, apiPollId)
        return readableDatabase.query(query, args)
            .use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) == 1
            }
    }

    fun create(identityPollModel: IdentityPollModel): Boolean {
        val contentValues = buildContentValues {
            put(IdentityPollModel.COLUMN_IDENTITY, identityPollModel.identity)
            put(IdentityPollModel.COLUMN_POLL_ID, identityPollModel.pollId)
        }
        val newId = writableDatabase.insertOrThrow(tableName, null, contentValues)
        if (newId > 0) {
            identityPollModel.id = newId.toInt()
            return true
        }
        return false
    }

    fun deleteByPollId(pollId: LocalPollId) {
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "${IdentityPollModel.COLUMN_POLL_ID}=?",
            whereArgs = arrayOf(pollId.toString()),
        )
    }

    class Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            "CREATE TABLE `identity_ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `identity` VARCHAR NOT NULL , `ballotId` INTEGER NOT NULL )",
            "CREATE UNIQUE INDEX `identityBallotId` ON `identity_ballot` ( `identity`, `ballotId` )",
        )
    }
}
