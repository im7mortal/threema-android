package ch.threema.storage.factories

import android.database.Cursor
import ch.threema.android.firstOrNull
import ch.threema.android.iterate
import ch.threema.common.DatabaseHelpers.convertToSelectionArgs
import ch.threema.common.DatabaseHelpers.makePlaceholders
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.LocalPollId
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.models.GroupId
import ch.threema.storage.CursorHelper
import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.buildContentValues
import ch.threema.storage.models.group.GroupModelOld
import ch.threema.storage.models.poll.GroupPollModel
import ch.threema.storage.models.poll.PollModel
import ch.threema.storage.runDelete
import ch.threema.storage.runQuery
import kotlin.collections.chunked

class GroupPollModelFactory(
    databaseProvider: DatabaseProvider,
) : ModelFactory(databaseProvider, GroupPollModel.TABLE) {

    fun getByGroupIdAndPollId(groupId: Int, pollId: LocalPollId): GroupPollModel? =
        getFirst(
            selection = "${GroupPollModel.COLUMN_GROUP_ID}=? AND ${GroupPollModel.COLUMN_POLL_ID}=?",
            selectionArgs = arrayOf(groupId.toString(), pollId.toString()),
        )

    fun getByPollId(pollId: LocalPollId): GroupPollModel? =
        getFirst(
            selection = "${GroupPollModel.COLUMN_POLL_ID}=?",
            selectionArgs = arrayOf(pollId.toString()),
        )

    private fun getFirst(selection: String?, selectionArgs: Array<String>?): GroupPollModel? =
        readableDatabase.runQuery(
            table = this.tableName,
            selection = selection,
            selectionArgs = selectionArgs,
        )
            .firstOrNull(::convert)

    fun getGroupIdentitiesByPollIds(pollIds: Set<LocalPollId>): Map<LocalPollId, GroupIdentity> = buildMap {
        pollIds
            .chunked(SqliteDatabaseBackend.MAX_VARIABLE_NUMBER)
            .forEach { pollIdsChunk ->
                val query = "SELECT p.${GroupPollModel.COLUMN_POLL_ID}, " +
                    "g.${GroupModelOld.COLUMN_CREATOR_IDENTITY}, g.${GroupModelOld.COLUMN_API_GROUP_ID} " +
                    "FROM $tableName AS p, ${GroupModelOld.TABLE} AS g " +
                    "WHERE p.${GroupPollModel.COLUMN_GROUP_ID} = g.${GroupModelOld.COLUMN_ID} " +
                    "AND p.${GroupPollModel.COLUMN_POLL_ID} IN (${makePlaceholders(pollIdsChunk.size)})"
                val args = convertToSelectionArgs(pollIdsChunk)
                readableDatabase.query(query, args)
                    .iterate { cursor ->
                        val pollId = cursor.getInt(0)
                        val creatorIdentity = cursor.getString(1)
                        val groupId = GroupId(cursor.getString(2))
                        put(pollId, GroupIdentity(creatorIdentity, groupId.toLong()))
                    }
            }
    }

    private fun convert(cursor: Cursor): GroupPollModel {
        val cursorHelper = CursorHelper(cursor, columnIndexCache)
        return GroupPollModel(
            id = cursorHelper.getInt(GroupPollModel.COLUMN_ID)!!,
            pollId = cursorHelper.getInt(GroupPollModel.COLUMN_POLL_ID)!!,
            groupId = cursorHelper.getInt(GroupPollModel.COLUMN_GROUP_ID)!!,
        )
    }

    fun exists(apiPollId: String, groupIdentity: GroupIdentity): Boolean {
        val query = "SELECT EXISTS(SELECT 1 FROM $tableName AS gp, ${GroupModelOld.TABLE} AS g, ${PollModel.TABLE} AS p " +
            "WHERE gp.${GroupPollModel.COLUMN_POLL_ID}=p.${PollModel.COLUMN_ID} AND " +
            "gp.${GroupPollModel.COLUMN_GROUP_ID}=g.${GroupModelOld.COLUMN_ID} AND " +
            "g.${GroupModelOld.COLUMN_CREATOR_IDENTITY}=? AND " +
            "g.${GroupModelOld.COLUMN_API_GROUP_ID}=? AND p.${PollModel.COLUMN_API_POLL_ID}=? LIMIT 1)"
        val args = arrayOf(groupIdentity.creatorIdentity, groupIdentity.apiGroupIdHexString, apiPollId)
        return readableDatabase.query(query, args)
            .use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) == 1
            }
    }

    fun create(groupPollModel: GroupPollModel): Boolean {
        val contentValues = buildContentValues {
            put(GroupPollModel.COLUMN_GROUP_ID, groupPollModel.groupId)
            put(GroupPollModel.COLUMN_POLL_ID, groupPollModel.pollId)
        }
        val newId = writableDatabase.insertOrThrow(this.tableName, null, contentValues)
        if (newId > 0) {
            groupPollModel.id = newId.toInt()
            return true
        }
        return false
    }

    fun deleteByPollId(pollId: LocalPollId) {
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "${GroupPollModel.COLUMN_POLL_ID}=?",
            whereArgs = arrayOf(pollId.toString()),
        )
    }

    class Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            "CREATE TABLE `group_ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `groupId` INTEGER NOT NULL , `ballotId` INTEGER NOT NULL )",
            "CREATE UNIQUE INDEX `groupBallotId` ON `group_ballot` ( `groupId`, `ballotId` )",
        )
    }
}
