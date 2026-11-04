package ch.threema.storage.factories

import android.content.ContentValues
import android.database.Cursor
import ch.threema.android.firstOrNull
import ch.threema.android.map
import ch.threema.android.readCountQueryResult
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.services.poll.PollService.PollFilter
import ch.threema.common.DatabaseHelpers.convertToSelectionArgs
import ch.threema.common.DatabaseHelpers.makePlaceholders
import ch.threema.data.datatypes.ApiPollId
import ch.threema.data.datatypes.LocalPollId
import ch.threema.domain.types.IdentityString
import ch.threema.storage.CursorHelper
import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.buildContentValues
import ch.threema.storage.deserializeEnum
import ch.threema.storage.getNonNullString
import ch.threema.storage.models.poll.GroupPollModel
import ch.threema.storage.models.poll.IdentityPollModel
import ch.threema.storage.models.poll.PollModel
import ch.threema.storage.models.poll.PollVoteModel
import ch.threema.storage.runDelete
import ch.threema.storage.runQuery
import ch.threema.storage.runUpdate

class PollModelFactory(
    databaseProvider: DatabaseProvider,
) : ModelFactory(databaseProvider, PollModel.TABLE) {

    fun getById(id: Int): PollModel? =
        getFirst(
            selection = "${PollModel.COLUMN_ID}=?",
            selectionArgs = arrayOf(id.toString()),
        )

    private fun getFirst(selection: String? = null, selectionArgs: Array<String>? = null): PollModel? =
        readableDatabase.runQuery(
            table = tableName,
            selection = selection,
            selectionArgs = selectionArgs,
        )
            .firstOrNull(::convert)

    private fun convert(cursor: Cursor): PollModel {
        val cursorHelper = CursorHelper(cursor, columnIndexCache)
        return PollModel(
            id = cursorHelper.getInt(PollModel.COLUMN_ID)!!,
            apiPollId = cursorHelper.getNonNullString(PollModel.COLUMN_API_POLL_ID),
            creatorIdentity = cursorHelper.getNonNullString(PollModel.COLUMN_CREATOR_IDENTITY),
            name = cursorHelper.getString(PollModel.COLUMN_NAME),
            createdAt = cursorHelper.getInstant(PollModel.COLUMN_CREATED_AT)!!,
            modifiedAt = cursorHelper.getInstant(PollModel.COLUMN_MODIFIED_AT)!!,
            lastViewedAt = cursorHelper.getInstant(PollModel.COLUMN_LAST_VIEWED_AT),
            state = cursorHelper.deserializeEnum(PollModel.COLUMN_STATE, PollModel.State::deserialize),
            assessment = cursorHelper.deserializeEnum(PollModel.COLUMN_ASSESSMENT, PollModel.Assessment::deserialize),
            type = cursorHelper.deserializeEnum(PollModel.COLUMN_TYPE, PollModel.Type::deserialize),
            choiceType = cursorHelper.deserializeEnum(PollModel.COLUMN_CHOICE_TYPE, PollModel.ChoiceType::deserialize),
            displayType = cursorHelper.getString(PollModel.COLUMN_DISPLAY_TYPE)
                ?.let(PollModel.DisplayType::deserialize),
        )
    }

    fun getApiPollIdByLocalPollId(pollId: LocalPollId): ApiPollId? =
        readableDatabase.runQuery(
            table = tableName,
            columns = arrayOf(PollModel.COLUMN_API_POLL_ID),
            selection = "${PollModel.COLUMN_ID}=?",
            selectionArgs = arrayOf(pollId.toString()),
        )
            .firstOrNull { cursor ->
                cursor.getString(0)
            }

    fun getLocalPollIdByApiPollId(apiPollId: ApiPollId): LocalPollId? =
        readableDatabase.runQuery(
            table = tableName,
            columns = arrayOf(PollModel.COLUMN_ID),
            selection = "${PollModel.COLUMN_API_POLL_ID}=?",
            selectionArgs = arrayOf(apiPollId),
        )
            .firstOrNull { cursor ->
                cursor.getInt(0)
            }

    fun createOrUpdate(pollModel: PollModel): Boolean {
        val alreadyExists = if (pollModel.id > 0) {
            readableDatabase.runQuery(
                table = tableName,
                selection = "${PollModel.COLUMN_ID}=?",
                selectionArgs = arrayOf(pollModel.id.toString()),
            )
                .use { cursor ->
                    cursor.moveToNext()
                }
        } else {
            false
        }
        if (alreadyExists) {
            update(pollModel)
            return true
        }
        return create(pollModel)
    }

    private fun buildContentValues(pollModel: PollModel): ContentValues = buildContentValues {
        put(PollModel.COLUMN_API_POLL_ID, pollModel.apiPollId)
        put(PollModel.COLUMN_CREATOR_IDENTITY, pollModel.creatorIdentity)
        put(PollModel.COLUMN_NAME, pollModel.name)
        put(PollModel.COLUMN_STATE, pollModel.state.serialized)
        put(PollModel.COLUMN_ASSESSMENT, pollModel.assessment.serialized)
        put(PollModel.COLUMN_TYPE, pollModel.type.serialized)
        put(PollModel.COLUMN_CHOICE_TYPE, pollModel.choiceType.serialized)
        put(PollModel.COLUMN_DISPLAY_TYPE, pollModel.displayType?.serialized)
        put(PollModel.COLUMN_CREATED_AT, pollModel.createdAt.toEpochMilli())
        put(PollModel.COLUMN_MODIFIED_AT, pollModel.modifiedAt.toEpochMilli())
        put(PollModel.COLUMN_LAST_VIEWED_AT, pollModel.lastViewedAt?.toEpochMilli())
    }

    fun create(pollModel: PollModel): Boolean {
        val contentValues = buildContentValues(pollModel)
        val newId = writableDatabase.insertOrThrow(tableName, null, contentValues)
        if (newId > 0) {
            pollModel.id = newId.toInt()
            return true
        }
        return false
    }

    fun update(pollModel: PollModel) {
        val contentValues = buildContentValues(pollModel)
        writableDatabase.runUpdate(
            table = tableName,
            values = contentValues,
            whereClause = "${PollModel.COLUMN_ID}=?",
            whereArgs = arrayOf(pollModel.id.toString()),
        )
    }

    fun delete(pollModel: PollModel): Int =
        writableDatabase.runDelete(
            table = tableName,
            whereClause = "${PollModel.COLUMN_ID}=?",
            whereArgs = arrayOf(pollModel.id.toString()),
        )

    fun count(filter: PollFilter): Long =
        runPollFilterQuery(filter, select = "SELECT COUNT(*)").readCountQueryResult()

    fun filter(filter: PollFilter?): List<PollModel> =
        runPollFilterQuery(filter, select = "SELECT DISTINCT b.*")
            ?.map(::convert)
            ?: emptyList()

    fun getByApiPollIdAndIdentity(apiPollId: String, groupCreator: IdentityString): PollModel? =
        getFirst(
            selection = "${PollModel.COLUMN_API_POLL_ID}=? AND ${PollModel.COLUMN_CREATOR_IDENTITY}=?",
            selectionArgs = arrayOf(apiPollId, groupCreator),
        )

    private fun runPollFilterQuery(filter: PollFilter?, select: String?): Cursor? {
        var query = "$select FROM $tableName b"
        val args = mutableListOf<String>()

        if (filter != null) {
            filter.getReceiver()?.let { receiver ->
                val linkTable: String
                val linkField: String
                val linkFieldReceiver: String
                val linkValue: String
                when (receiver) {
                    is GroupMessageReceiver -> {
                        linkTable = GroupPollModel.TABLE
                        linkField = GroupPollModel.COLUMN_POLL_ID
                        linkFieldReceiver = GroupPollModel.COLUMN_GROUP_ID
                        linkValue = receiver.group.id.toString()
                    }
                    is ContactMessageReceiver -> {
                        linkTable = IdentityPollModel.TABLE
                        linkField = IdentityPollModel.COLUMN_POLL_ID
                        linkFieldReceiver = IdentityPollModel.COLUMN_IDENTITY
                        linkValue = receiver.contact.identity
                    }
                    else -> return null
                }
                query += " INNER JOIN $linkTable l ON l.$linkField = b.${PollModel.COLUMN_ID} AND l.$linkFieldReceiver = ?"
                args.add(linkValue)
            }

            val where = mutableListOf<String>()

            if (filter.getStates() != null && filter.getStates().size > 0) {
                where.add("b.${PollModel.COLUMN_STATE} IN (${makePlaceholders(filter.getStates().size)})")
                for (f in filter.getStates()) {
                    args.add(f.serialized)
                }
            }

            filter.createdOrNotVotedByIdentity()?.let { identity ->
                where.add(
                    "b.${PollModel.COLUMN_CREATOR_IDENTITY} = ? OR NOT EXISTS (" +
                        "SELECT sv.${PollVoteModel.COLUMN_POLL_ID} FROM ${PollVoteModel.TABLE} sv WHERE " +
                        "sv.${PollVoteModel.COLUMN_VOTING_IDENTITY} = ? AND sv.${PollVoteModel.COLUMN_POLL_ID} = b.${PollModel.COLUMN_ID})",
                )
                args.add(identity)
                args.add(identity)
            }

            if (!where.isEmpty()) {
                query += " WHERE (${where.joinToString(separator = ") AND (")})"
            }

            query += " ORDER BY b.${PollModel.COLUMN_CREATED_AT} DESC"
        }

        return readableDatabase.rawQuery(
            query,
            convertToSelectionArgs(args),
        )
    }

    fun getBatch(count: Int, offset: Int): List<PollModel> {
        val query = "SELECT * FROM `$tableName` LIMIT ? OFFSET ?"
        val args = arrayOf(count.toString(), offset.toString())
        return readableDatabase.query(query, args)
            .map(::convert)
    }

    class Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            "CREATE TABLE `ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `apiBallotId` VARCHAR NOT NULL , `creatorIdentity` VARCHAR NOT NULL , " +
                "`name` VARCHAR , `state` VARCHAR NOT NULL , `assessment` VARCHAR NOT NULL , `type` VARCHAR NOT NULL , " +
                "`choiceType` VARCHAR NOT NULL , `displayType` VARCHAR , `createdAt` BIGINT NOT NULL , `modifiedAt` BIGINT NOT NULL , " +
                "`lastViewedAt` BIGINT )",
            "CREATE UNIQUE INDEX `apiBallotIdAndCreator` ON `ballot` ( `apiBallotId`, `creatorIdentity` )",
        )
    }
}
