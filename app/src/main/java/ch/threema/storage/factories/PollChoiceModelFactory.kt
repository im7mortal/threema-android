package ch.threema.storage.factories

import android.content.ContentValues
import android.database.Cursor
import ch.threema.android.firstOrNull
import ch.threema.android.map
import ch.threema.common.DatabaseHelpers.convertToSelectionArgs
import ch.threema.common.DatabaseHelpers.makePlaceholders
import ch.threema.data.datatypes.ApiPollChoiceId
import ch.threema.data.datatypes.LocalPollId
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.storage.CursorHelper
import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.buildContentValues
import ch.threema.storage.models.poll.PollChoiceModel
import ch.threema.storage.runDelete
import ch.threema.storage.runQuery
import ch.threema.storage.runUpdate

class PollChoiceModelFactory(
    databaseProvider: DatabaseProvider,
) : ModelFactory(databaseProvider, PollChoiceModel.TABLE) {

    fun getByPollId(pollId: LocalPollId): List<PollChoiceModel> =
        readableDatabase.runQuery(
            table = tableName,
            selection = "${PollChoiceModel.COLUMN_POLL_ID}=?",
            selectionArgs = arrayOf(pollId.toString()),
            orderBy = "`${PollChoiceModel.COLUMN_ORDER}` ASC",
        )
            .map(::convert)

    fun getByPollIdAndApiChoiceId(pollId: LocalPollId, apiChoiceId: ApiPollChoiceId): PollChoiceModel? =
        readableDatabase.runQuery(
            table = tableName,
            selection = "${PollChoiceModel.COLUMN_POLL_ID}=? AND ${PollChoiceModel.COLUMN_API_CHOICE_ID}=?",
            selectionArgs = arrayOf(pollId.toString(), apiChoiceId.toString()),
        )
            .firstOrNull(::convert)

    fun getByPollIds(pollIds: Set<LocalPollId>): Map<LocalPollId, List<PollChoiceModel>> =
        pollIds
            .chunked(SqliteDatabaseBackend.MAX_VARIABLE_NUMBER)
            .flatMap { pollIdsChunk ->
                readableDatabase.runQuery(
                    table = tableName,
                    selection = "${PollChoiceModel.COLUMN_POLL_ID} IN (${makePlaceholders(pollIdsChunk.size)})",
                    selectionArgs = convertToSelectionArgs(pollIdsChunk),
                    orderBy = "`${PollChoiceModel.COLUMN_ORDER}` ASC",
                )
                    .map(::convert)
            }
            .groupBy(PollChoiceModel::pollId)

    fun createOrUpdate(pollChoiceModel: PollChoiceModel): Boolean {
        val alreadyExists = if (pollChoiceModel.id > 0) {
            readableDatabase.runQuery(
                table = tableName,
                selection = "${PollChoiceModel.COLUMN_ID}=?",
                selectionArgs = arrayOf(pollChoiceModel.id.toString()),
            )
                .use { cursor ->
                    cursor.moveToNext()
                }
        } else {
            false
        }

        return if (alreadyExists) {
            update(pollChoiceModel)
        } else {
            create(pollChoiceModel)
        }
    }

    private fun buildContentValues(pollChoiceModel: PollChoiceModel): ContentValues =
        buildContentValues {
            put(PollChoiceModel.COLUMN_POLL_ID, pollChoiceModel.pollId)
            put(PollChoiceModel.COLUMN_API_CHOICE_ID, pollChoiceModel.apiPollChoiceId)
            put(PollChoiceModel.COLUMN_TYPE, pollChoiceModel.type.serialized)
            put(PollChoiceModel.COLUMN_NAME, pollChoiceModel.name)
            put(PollChoiceModel.COLUMN_VOTE_COUNT, pollChoiceModel.voteCount)
            put("`${PollChoiceModel.COLUMN_ORDER}`", pollChoiceModel.order)
            put(PollChoiceModel.COLUMN_CREATED_AT, pollChoiceModel.createdAt?.toEpochMilli())
            put(PollChoiceModel.COLUMN_MODIFIED_AT, pollChoiceModel.modifiedAt?.toEpochMilli())
        }

    fun create(pollChoiceModel: PollChoiceModel): Boolean {
        val contentValues = buildContentValues(pollChoiceModel)
        val newId = writableDatabase.insertOrThrow(tableName, null, contentValues)
        if (newId > 0) {
            pollChoiceModel.id = newId.toInt()
            return true
        }
        return false
    }

    private fun update(pollChoiceModel: PollChoiceModel): Boolean {
        val contentValues = buildContentValues(pollChoiceModel)
        writableDatabase.runUpdate(
            table = tableName,
            values = contentValues,
            whereClause = "${PollChoiceModel.COLUMN_ID}=?",
            whereArgs = arrayOf(pollChoiceModel.id.toString()),
        )
        return true
    }

    fun delete(pollChoiceModel: PollChoiceModel): Int =
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "${PollChoiceModel.COLUMN_ID}=?",
            whereArgs = arrayOf(pollChoiceModel.id.toString()),
        )

    fun deleteByPollId(pollId: LocalPollId) {
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "${PollChoiceModel.COLUMN_POLL_ID}=?",
            whereArgs = arrayOf(pollId.toString()),
        )
    }

    private fun convert(cursor: Cursor): PollChoiceModel {
        val cursorHelper = CursorHelper(cursor, columnIndexCache)
        return PollChoiceModel(
            id = cursorHelper.getInt(PollChoiceModel.COLUMN_ID)!!,
            pollId = cursorHelper.getInt(PollChoiceModel.COLUMN_POLL_ID)!!,
            apiPollChoiceId = cursorHelper.getInt(PollChoiceModel.COLUMN_API_CHOICE_ID)!!,
            name = cursorHelper.getString(PollChoiceModel.COLUMN_NAME),
            voteCount = cursorHelper.getInt(PollChoiceModel.COLUMN_VOTE_COUNT)!!,
            order = cursorHelper.getInt(PollChoiceModel.COLUMN_ORDER)!!,
            createdAt = cursorHelper.getInstant(PollChoiceModel.COLUMN_CREATED_AT),
            modifiedAt = cursorHelper.getInstant(PollChoiceModel.COLUMN_MODIFIED_AT),
        )
    }

    class Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            "CREATE TABLE `ballot_choice` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `ballotId` INTEGER , `apiBallotChoiceId` INTEGER , `type` VARCHAR , `name` VARCHAR , `voteCount` INTEGER , `order` INTEGER NOT NULL , `createdAt` BIGINT , `modifiedAt` BIGINT )",
            "CREATE UNIQUE INDEX `apiBallotChoiceId` ON `ballot_choice` ( `ballotId`, `apiBallotChoiceId` )",
        )
    }
}
