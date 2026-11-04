package ch.threema.data.storage

import android.database.Cursor
import androidx.core.database.getLongOrNull
import ch.threema.android.iterate
import ch.threema.android.map
import ch.threema.app.utils.ThrowingConsumer
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DatabaseHelpers.convertToSelectionArgs
import ch.threema.common.DatabaseHelpers.makePlaceholders
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.repositories.EmojiReactionEntryCreateException
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.buildContentValues
import ch.threema.storage.factories.ContactEmojiReactionModelFactory
import ch.threema.storage.factories.GroupEmojiReactionModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.group.GroupModelOld
import ch.threema.storage.runQuery
import ch.threema.storage.runTransaction
import ch.threema.storage.runUpdate
import java.time.Instant
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("EmojiReactionsDaoImpl")

class EmojiReactionsDaoImpl(
    private val databaseProvider: DatabaseProvider,
) : EmojiReactionsDao {
    override fun create(entry: DbEmojiReaction, messageModel: MessageModel) {
        val table = getReactionTableForMessage(messageModel)
            ?: throw EmojiReactionEntryCreateException(
                IllegalArgumentException("Cannot create reaction entry for message of class ${messageModel.javaClass.name}"),
            )
        databaseProvider.writableDatabase.insert(
            table = table,
            conflictAlgorithm = SQLiteDatabase.CONFLICT_ROLLBACK,
            values = entry.getContentValues(),
        )
    }

    override fun remove(entry: DbEmojiReaction, messageModel: MessageModel) {
        val table = getReactionTableForMessage(messageModel) ?: return

        val removedEntries = databaseProvider.writableDatabase.delete(
            table = table,
            whereClause = "${DbEmojiReaction.COLUMN_MESSAGE_ID} = ? AND ${DbEmojiReaction.COLUMN_EMOJI_SEQUENCE} = ? AND " +
                "${DbEmojiReaction.COLUMN_SENDER_IDENTITY} = ?",
            whereArgs = arrayOf<Any>(entry.messageId, entry.emojiSequence, entry.senderIdentity),
        )
        logger.debug("{} entries removed for reaction {}", removedEntries, entry)
    }

    override fun deleteAllByMessage(messageModel: MessageModel) {
        val table = getReactionTableForMessage(messageModel) ?: return

        val deletedEntries = databaseProvider.writableDatabase.delete(
            table = table,
            whereClause = "${DbEmojiReaction.COLUMN_MESSAGE_ID} = ?",
            whereArgs = arrayOf(messageModel.id),
        )
        logger.debug(
            "{} reaction entries deleted for message {} ({})",
            deletedEntries,
            messageModel.id,
            table,
        )
    }

    override fun findAllByMessage(messageModel: MessageModel): List<DbEmojiReaction> {
        val table = getReactionTableForMessage(messageModel) ?: return emptyList()

        val query = "SELECT * FROM $table WHERE ${DbEmojiReaction.COLUMN_MESSAGE_ID} = ? " +
            "ORDER BY ${DbEmojiReaction.COLUMN_REACTED_AT} DESC"

        val cursor = databaseProvider.readableDatabase
            .rawQuery(
                query,
                messageModel.id,
            )

        return cursor.map(::convert)
    }

    override fun findAllByMessages(messageModels: List<MessageModel>): List<DbEmojiReaction> {
        val groupedMessageModels = messageModels.groupBy { messageModel ->
            getReactionTableForMessage(messageModel)
        }
        return buildList {
            groupedMessageModels.forEach { (table, messageModels) ->
                table ?: return@forEach
                messageModels
                    .chunked(SqliteDatabaseBackend.MAX_VARIABLE_NUMBER) { messageModelsChunk ->
                        val args = convertToSelectionArgs(messageModelsChunk) { it.id.toString() }
                        databaseProvider.readableDatabase.runQuery(
                            table = table,
                            selection = "${DbEmojiReaction.COLUMN_MESSAGE_ID} IN (${makePlaceholders(args.size)})",
                            selectionArgs = args,
                            orderBy = "${DbEmojiReaction.COLUMN_REACTED_AT} DESC",
                        )
                            .iterate { cursor ->
                                add(convert(cursor))
                            }
                    }
            }
        }
            .sortedByDescending { it.reactedAt }
    }

    override fun markUnreadReactionsAsReadForContactConversation(contactConversationId: ContactConversationId, readAt: Instant): Set<Int> =
        markUnreadReactionsAsRead(
            reactionTable = ContactEmojiReactionModelFactory.TABLE,
            messageIdsSubQuery = "SELECT ${MessageModel.COLUMN_ID} FROM ${ContactMessageModel.TABLE} WHERE ${MessageModel.COLUMN_IDENTITY} = ?",
            subQueryArgs = arrayOf(contactConversationId.identity),
            readAt = readAt,
        )

    override fun markUnreadReactionsAsReadForGroupConversation(groupConversationId: GroupConversationId, readAt: Instant): Set<Int> =
        markUnreadReactionsAsRead(
            reactionTable = GroupEmojiReactionModelFactory.TABLE,
            messageIdsSubQuery = "SELECT ${MessageModel.COLUMN_ID} FROM ${GroupMessageModel.TABLE} WHERE ${GroupMessageModel.COLUMN_GROUP_ID} = ?",
            subQueryArgs = arrayOf(groupConversationId.groupDatabaseId.toString()),
            readAt = readAt,
        )

    private fun markUnreadReactionsAsRead(
        reactionTable: String,
        messageIdsSubQuery: String,
        subQueryArgs: Array<String>,
        readAt: Instant,
    ): Set<Int> {
        val unreadSelection = "${DbEmojiReaction.COLUMN_READ_AT} IS NULL AND ${DbEmojiReaction.COLUMN_MESSAGE_ID} IN ($messageIdsSubQuery)"
        val affectedMessageIds = databaseProvider.readableDatabase
            .rawQuery(
                "SELECT DISTINCT ${DbEmojiReaction.COLUMN_MESSAGE_ID} FROM `$reactionTable` WHERE $unreadSelection",
                subQueryArgs,
            )
            .map { cursor -> cursor.getInt(0) }
            .toSet()
        if (affectedMessageIds.isEmpty()) {
            return emptySet()
        }
        val updatedEntries = databaseProvider.writableDatabase.runUpdate(
            table = reactionTable,
            values = buildContentValues {
                put(DbEmojiReaction.COLUMN_READ_AT, readAt.toEpochMilli())
            },
            whereClause = unreadSelection,
            whereArgs = subQueryArgs,
        )
        logger.debug("{} unread reactions in table {} marked as read", updatedEntries, reactionTable)
        return affectedMessageIds
    }

    override fun deleteAll() {
        databaseProvider.writableDatabase.runTransaction {
            execSQL("DELETE FROM ${ContactEmojiReactionModelFactory.TABLE}")
            execSQL("DELETE FROM ${GroupEmojiReactionModelFactory.TABLE}")
        }
    }

    override fun getContactReactionsCount(): Long =
        getReactionCount(ContactEmojiReactionModelFactory.TABLE)

    override fun getGroupReactionsCount(): Long =
        getReactionCount(GroupEmojiReactionModelFactory.TABLE)

    private fun getReactionCount(table: String): Long {
        val query = "SELECT COUNT(*) FROM `$table`"
        return databaseProvider.readableDatabase
            .query(query)
            .use {
                if (it.moveToFirst()) {
                    it.getLong(0)
                } else {
                    0
                }
            }
    }

    override fun iterateAllContactBackupReactions(consumer: ThrowingConsumer<EmojiReactionsDao.BackupContactReaction>) {
        val resultColumnContactIdentity = "contactIdentity"
        val resultColumnApiMessageId = "apiId"
        val resultColumnSenderIdentity = "senderIdentity"
        val resultColumnEmojiSequence = "emojiSequence"
        val resultColumnReactedAt = "reactedAt"

        val query = """
            SELECT
                m.${MessageModel.COLUMN_IDENTITY} as $resultColumnContactIdentity,
                m.${MessageModel.COLUMN_API_MESSAGE_ID} as $resultColumnApiMessageId,
                r.${DbEmojiReaction.COLUMN_SENDER_IDENTITY} as $resultColumnSenderIdentity,
                r.${DbEmojiReaction.COLUMN_EMOJI_SEQUENCE} as $resultColumnEmojiSequence,
                r.${DbEmojiReaction.COLUMN_REACTED_AT} as $resultColumnReactedAt
            FROM ${ContactEmojiReactionModelFactory.TABLE} r JOIN ${ContactMessageModel.TABLE} m
            ON r.${DbEmojiReaction.COLUMN_MESSAGE_ID} = m.${MessageModel.COLUMN_ID}
            ORDER BY r.${DbEmojiReaction.COLUMN_MESSAGE_ID}
        """.trimIndent()

        databaseProvider.readableDatabase.query(query).use { cursor ->
            val columnIndexContactIdentity = cursor.getColumnIndexOrThrow(resultColumnContactIdentity)
            val columnIndexApiMessageId = cursor.getColumnIndexOrThrow(resultColumnApiMessageId)
            val columnIndexSenderIdentity = cursor.getColumnIndexOrThrow(resultColumnSenderIdentity)
            val columnIndexEmojiSequence = cursor.getColumnIndexOrThrow(resultColumnEmojiSequence)
            val columnIndexReactedAt = cursor.getColumnIndexOrThrow(resultColumnReactedAt)

            while (cursor.moveToNext()) {
                tryHandlingReactionEntry {
                    consumer.accept(
                        EmojiReactionsDao.BackupContactReaction(
                            contactIdentity = cursor.getString(columnIndexContactIdentity),
                            apiMessageId = cursor.getString(columnIndexApiMessageId),
                            senderIdentity = cursor.getString(columnIndexSenderIdentity),
                            emojiSequence = cursor.getString(columnIndexEmojiSequence),
                            reactedAt = cursor.getLong(columnIndexReactedAt),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Results are ordered by the message id
     */
    override fun iterateAllGroupBackupReactions(consumer: ThrowingConsumer<EmojiReactionsDao.BackupGroupReaction>) {
        val resultColumnGroupId = "groupId"
        val resultColumnGroupCreatorIdentity = "groupCreatorIdentity"
        val resultColumnApiMessageId = "apiId"
        val resultColumnSenderIdentity = "senderIdentity"
        val resultColumnEmojiSequence = "emojiSequence"
        val resultColumnReactedAt = "reactedAt"

        val query = """
            SELECT
                g.${GroupModelOld.COLUMN_API_GROUP_ID} as $resultColumnGroupId,
                g.${GroupModelOld.COLUMN_CREATOR_IDENTITY} as $resultColumnGroupCreatorIdentity,
                m.${MessageModel.COLUMN_API_MESSAGE_ID} as $resultColumnApiMessageId,
                r.${DbEmojiReaction.COLUMN_SENDER_IDENTITY} as $resultColumnSenderIdentity,
                r.${DbEmojiReaction.COLUMN_EMOJI_SEQUENCE} as $resultColumnEmojiSequence,
                r.${DbEmojiReaction.COLUMN_REACTED_AT} as $resultColumnReactedAt
            FROM
                ${GroupEmojiReactionModelFactory.TABLE} r,
                ${GroupMessageModel.TABLE} m,
                ${GroupModelOld.TABLE} g
            WHERE
                r.${DbEmojiReaction.COLUMN_MESSAGE_ID} = m.${MessageModel.COLUMN_ID}
                AND m.${GroupMessageModel.COLUMN_GROUP_ID} = g.${GroupModelOld.COLUMN_ID}
            ORDER BY r.${DbEmojiReaction.COLUMN_MESSAGE_ID}
        """.trimIndent()

        databaseProvider.readableDatabase.query(query).use { cursor ->
            val columnIndexGroupId = cursor.getColumnIndexOrThrow(resultColumnGroupId)
            val columnIndexGroupCreatorIdentity = cursor.getColumnIndexOrThrow(resultColumnGroupCreatorIdentity)
            val columnIndexApiMessageId = cursor.getColumnIndexOrThrow(resultColumnApiMessageId)
            val columnIndexSenderIdentity = cursor.getColumnIndexOrThrow(resultColumnSenderIdentity)
            val columnIndexEmojiSequence = cursor.getColumnIndexOrThrow(resultColumnEmojiSequence)
            val columnIndexReactedAt = cursor.getColumnIndexOrThrow(resultColumnReactedAt)

            while (cursor.moveToNext()) {
                tryHandlingReactionEntry {
                    consumer.accept(
                        EmojiReactionsDao.BackupGroupReaction(
                            apiGroupId = cursor.getString(columnIndexGroupId),
                            groupCreatorIdentity = cursor.getString(columnIndexGroupCreatorIdentity),
                            apiMessageId = cursor.getString(columnIndexApiMessageId),
                            senderIdentity = cursor.getString(columnIndexSenderIdentity),
                            emojiSequence = cursor.getString(columnIndexEmojiSequence),
                            reactedAt = cursor.getLong(columnIndexReactedAt),
                        ),
                    )
                }
            }
        }
    }

    private fun tryHandlingReactionEntry(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.error("Skip invalid reaction", e)
        }
    }

    private fun getReactionTableForMessage(messageModel: MessageModel): String? =
        when (messageModel) {
            is GroupMessageModel -> GroupEmojiReactionModelFactory.TABLE
            is ContactMessageModel -> ContactEmojiReactionModelFactory.TABLE
            is DistributionListMessageModel -> null
        }

    private fun convert(cursor: Cursor): DbEmojiReaction {
        val messageId = cursor.getInt(
            getColumnIndexOrThrow(
                cursor,
                DbEmojiReaction.COLUMN_MESSAGE_ID,
            ),
        )
        val senderIdentity = cursor.getString(
            getColumnIndexOrThrow(
                cursor,
                DbEmojiReaction.COLUMN_SENDER_IDENTITY,
            ),
        )
        val emojiSequence = cursor.getString(
            getColumnIndexOrThrow(
                cursor,
                DbEmojiReaction.COLUMN_EMOJI_SEQUENCE,
            ),
        )
        val reactedAt = cursor.getInstant(
            getColumnIndexOrThrow(
                cursor,
                DbEmojiReaction.COLUMN_REACTED_AT,
            ),
        )
        val readAt = cursor.getLongOrNull(
            getColumnIndexOrThrow(
                cursor,
                DbEmojiReaction.COLUMN_READ_AT,
            ),
        )?.let(Instant::ofEpochMilli)

        return DbEmojiReaction(
            messageId = messageId,
            senderIdentity = senderIdentity,
            emojiSequence = emojiSequence,
            reactedAt = reactedAt,
            readAt = readAt,
        )
    }

    override fun insertContactReactionsInTransaction(block: EmojiReactionsDao.TransactionalReactionInsertScope) {
        insertReactionsInTransaction(
            ContactEmojiReactionModelFactory.TABLE,
            block,
        )
    }

    override fun insertGroupReactionsInTransaction(block: EmojiReactionsDao.TransactionalReactionInsertScope) {
        insertReactionsInTransaction(
            GroupEmojiReactionModelFactory.TABLE,
            block,
        )
    }

    private fun insertReactionsInTransaction(
        table: String,
        block: EmojiReactionsDao.TransactionalReactionInsertScope,
    ) {
        databaseProvider.writableDatabase.runTransaction {
            block.runInserts { entry ->
                val success = insert(
                    table = table,
                    conflictAlgorithm = SQLiteDatabase.CONFLICT_IGNORE,
                    values = entry.getContentValues(),
                ) >= 0
                logger.debug("Insert reaction {}, success={}", entry, success)
            }
        }
    }

    private fun DbEmojiReaction.getContentValues() = buildContentValues {
        put(DbEmojiReaction.COLUMN_MESSAGE_ID, messageId)
        put(DbEmojiReaction.COLUMN_SENDER_IDENTITY, senderIdentity)
        put(DbEmojiReaction.COLUMN_EMOJI_SEQUENCE, emojiSequence)
        put(DbEmojiReaction.COLUMN_REACTED_AT, reactedAt.toEpochMilli())
        put(DbEmojiReaction.COLUMN_READ_AT, readAt?.toEpochMilli())
    }
}
