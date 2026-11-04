package ch.threema.storage.factories

import android.content.ContentValues
import android.database.Cursor
import ch.threema.android.map
import ch.threema.common.DatabaseHelpers.convertToSelectionArgs
import ch.threema.common.DatabaseHelpers.makePlaceholders
import ch.threema.data.datatypes.LocalPollId
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.types.IdentityString
import ch.threema.storage.CursorHelper
import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.buildContentValues
import ch.threema.storage.getNonNullInstant
import ch.threema.storage.models.poll.PollVoteModel
import ch.threema.storage.runDelete
import ch.threema.storage.runQuery
import ch.threema.storage.runUpdate
import net.zetetic.database.DatabaseUtils

class PollVoteModelFactory(
    databaseProvider: DatabaseProvider,
) : ModelFactory(databaseProvider, PollVoteModel.TABLE) {
    fun getByPollId(pollId: LocalPollId): List<PollVoteModel> =
        convertList(
            readableDatabase.runQuery(
                table = tableName,
                selection = "${PollVoteModel.COLUMN_POLL_ID}=?",
                selectionArgs = arrayOf(pollId.toString()),
            ),
        )

    fun getByPollIdAndVotingIdentity(
        pollId: LocalPollId,
        fromIdentity: IdentityString,
    ): List<PollVoteModel> =
        convertList(
            readableDatabase.runQuery(
                table = tableName,
                selection = "${PollVoteModel.COLUMN_POLL_ID}=? AND ${PollVoteModel.COLUMN_VOTING_IDENTITY}=?",
                selectionArgs = arrayOf(pollId.toString(), fromIdentity),
            ),
        )

    fun countByPollIdAndVotingIdentity(pollId: LocalPollId, fromIdentity: IdentityString): Long =
        DatabaseUtils.longForQuery(
            readableDatabase,
            "SELECT COUNT(*) FROM $tableName WHERE ${PollVoteModel.COLUMN_POLL_ID}=? AND ${PollVoteModel.COLUMN_VOTING_IDENTITY}=?",
            arrayOf(pollId.toString(), fromIdentity),
        )

    private fun convertList(cursor: Cursor): List<PollVoteModel> =
        cursor.map(::convert)

    fun getByPollIds(pollIds: Set<LocalPollId>): Map<LocalPollId, List<PollVoteModel>> =
        pollIds
            .chunked(SqliteDatabaseBackend.MAX_VARIABLE_NUMBER)
            .flatMap { pollIdsChunk ->
                readableDatabase.runQuery(
                    table = tableName,
                    selection = "${PollVoteModel.COLUMN_POLL_ID} IN (${makePlaceholders(pollIdsChunk.size)})",
                    selectionArgs = convertToSelectionArgs(pollIdsChunk),
                )
                    .map(::convert)
            }
            .groupBy(PollVoteModel::pollId)

    private fun convert(cursor: Cursor): PollVoteModel {
        val cursorHelper = CursorHelper(cursor, columnIndexCache)
        return PollVoteModel(
            id = cursorHelper.getInt(PollVoteModel.COLUMN_ID)!!,
            pollId = cursorHelper.getInt(PollVoteModel.COLUMN_POLL_ID)!!,
            pollChoiceId = cursorHelper.getInt(PollVoteModel.COLUMN_POLL_CHOICE_ID)!!,
            votingIdentity = cursorHelper.getString(PollVoteModel.COLUMN_VOTING_IDENTITY),
            selected = cursorHelper.getInt(PollVoteModel.COLUMN_CHOICE)!! > 0,
            createdAt = cursorHelper.getNonNullInstant(PollVoteModel.COLUMN_CREATED_AT),
            modifiedAt = cursorHelper.getNonNullInstant(PollVoteModel.COLUMN_MODIFIED_AT),
        )
    }

    fun createOrUpdate(pollVoteModel: PollVoteModel): Boolean {
        val alreadyExists = if (pollVoteModel.id > 0) {
            readableDatabase.runQuery(
                table = tableName,
                selection = "${PollVoteModel.COLUMN_ID}=?",
                selectionArgs = arrayOf(pollVoteModel.id.toString()),
            )
                .use { cursor ->
                    cursor.moveToNext()
                }
        } else {
            false
        }
        if (alreadyExists) {
            update(pollVoteModel)
            return true
        }
        return create(pollVoteModel)
    }

    fun create(pollVoteModel: PollVoteModel): Boolean {
        val contentValues = buildContentValues(pollVoteModel)
        val newId = writableDatabase.insertOrThrow(tableName, null, contentValues)
        if (newId > 0) {
            pollVoteModel.id = newId.toInt()
            return true
        }
        return false
    }

    private fun update(pollVoteModel: PollVoteModel) {
        val contentValues = buildContentValues(pollVoteModel)
        writableDatabase.runUpdate(
            table = tableName,
            values = contentValues,
            whereClause = "${PollVoteModel.COLUMN_ID}=?",
            whereArgs = arrayOf(pollVoteModel.id.toString()),
        )
    }

    private fun buildContentValues(pollVoteModel: PollVoteModel): ContentValues =
        buildContentValues {
            put(PollVoteModel.COLUMN_POLL_ID, pollVoteModel.pollId)
            put(PollVoteModel.COLUMN_POLL_CHOICE_ID, pollVoteModel.pollChoiceId)
            put(PollVoteModel.COLUMN_VOTING_IDENTITY, pollVoteModel.votingIdentity)
            put(PollVoteModel.COLUMN_CHOICE, pollVoteModel.choice)
            put(PollVoteModel.COLUMN_CREATED_AT, pollVoteModel.createdAt?.toEpochMilli())
            put(PollVoteModel.COLUMN_MODIFIED_AT, pollVoteModel.modifiedAt?.toEpochMilli())
        }

    fun delete(pollVoteModel: PollVoteModel): Int =
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "${PollVoteModel.COLUMN_ID}=?",
            whereArgs = arrayOf(pollVoteModel.id.toString()),
        )

    fun deleteByIds(ids: IntArray): Int {
        val args = ids.map { it.toString() }.toTypedArray()
        return writableDatabase.runDelete(
            table = tableName,
            whereClause = "${PollVoteModel.COLUMN_ID} IN (${makePlaceholders(args.size)})",
            whereArgs = args,
        )
    }

    fun deleteByPollId(pollId: LocalPollId): Int =
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "${PollVoteModel.COLUMN_POLL_ID}=?",
            whereArgs = arrayOf(pollId.toString()),
        )

    fun deleteByPollIdAndVotingIdentity(pollId: LocalPollId, identity: String?): Int =
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "${PollVoteModel.COLUMN_POLL_ID}=? AND ${PollVoteModel.COLUMN_VOTING_IDENTITY}=?",
            whereArgs = arrayOf(pollId.toString(), identity),
        )

    fun countByPollChoiceIdAndChoice(pollChoiceId: Int, choice: Int): Long =
        DatabaseUtils.longForQuery(
            readableDatabase,
            "SELECT COUNT(*) FROM $tableName WHERE ${PollVoteModel.COLUMN_POLL_CHOICE_ID}=? AND ${PollVoteModel.COLUMN_CHOICE}=?",
            arrayOf(pollChoiceId.toString(), choice.toString()),
        )

    class Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            "CREATE TABLE `ballot_vote` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `ballotId` INTEGER NOT NULL , " +
                "`ballotChoiceId` INTEGER NOT NULL , `votingIdentity` VARCHAR NOT NULL , `choice` INTEGER , `createdAt` BIGINT NOT NULL , " +
                "`modifiedAt` BIGINT NOT NULL );",
            "CREATE INDEX `ballotVotingCount` ON `ballot_vote` ( `ballotChoiceId`, `choice` )",
            "CREATE UNIQUE INDEX `ballotVoteIdentity` ON `ballot_vote` ( `ballotId`, `ballotChoiceId`, `votingIdentity` );",
        )
    }
}
