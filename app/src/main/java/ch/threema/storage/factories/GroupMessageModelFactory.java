package ch.threema.storage.factories;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.MessageService;
import ch.threema.data.datatypes.ConversationVisibility;
import ch.threema.data.datatypes.GroupMessageIdentifier;
import ch.threema.data.storage.SqliteDatabaseBackend;
import ch.threema.domain.models.MessageId;
import ch.threema.storage.ChunkedSequence;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseExtensionsKt;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.data.models.GroupModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.group.GroupModelOld;
import kotlin.ranges.LongProgression;

import static ch.threema.android.CursorExtensionsKt.readCountQueryResult;
import static ch.threema.common.DatabaseHelpers.makePlaceholders;
import static ch.threema.common.JsonExtensionsKt.parseJsonObjectAsStringMap;
import static ch.threema.storage.models.data.DisplayTag.DISPLAY_TAG_STARRED;

public class GroupMessageModelFactory extends MessageModelFactory {
    public GroupMessageModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, GroupMessageModel.TABLE);
    }

    public List<GroupMessageModel> getAll() {
        return convertList(getReadableDatabase().query(this.getTableName(), null, null, null, null, null, null));
    }

    public GroupMessageModel getByApiMessageIdAndIdentity(@NonNull MessageId apiMessageId, String identity) {
        return getFirst(
            GroupMessageModel.COLUMN_API_MESSAGE_ID + "=? AND " + GroupMessageModel.COLUMN_IDENTITY + "=?",
            new String[]{
                apiMessageId.toString(),
                identity
            });
    }

    public GroupMessageModel getByApiMessageIdAndGroupId(@NonNull MessageId apiMessageId, int groupId) {
        return getFirst(
            GroupMessageModel.COLUMN_API_MESSAGE_ID + "=? AND " + GroupMessageModel.COLUMN_GROUP_ID + "=?",
            new String[]{
                apiMessageId.toString(),
                String.valueOf(groupId),
            });
    }

    /**
     * @return {@code true} if there is at least one outgoing message in this group that is not a status message.
     */
    public boolean existsOutgoingContentMessage(final long groupDatabaseId) {
        if (groupDatabaseId < 0) {
            return false;
        }
        final @Nullable Cursor cursor = getReadableDatabase()
            .rawQuery(
                "SELECT EXISTS(SELECT 1 FROM " + GroupMessageModel.TABLE + " WHERE "
                    + GroupMessageModel.COLUMN_GROUP_ID + " = ? AND "
                    + GroupMessageModel.COLUMN_OUTBOX + " = 1 AND "
                    + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0 LIMIT 1)",
                new String[]{
                    String.valueOf(groupDatabaseId)
                }
            );
        if (cursor == null) {
            return false;
        }
        try (cursor) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    /**
     * @return The earliest (created-at) incoming message that is not a status message.
     */
    @Nullable
    public GroupMessageModel getEarliestIncomingContentMessage(final long groupDatabaseId) {
        if (groupDatabaseId < 0) {
            return null;
        }
        final @Nullable Cursor cursor = getReadableDatabase()
            .rawQuery(
                "SELECT * FROM " + GroupMessageModel.TABLE + " WHERE "
                    + GroupMessageModel.COLUMN_GROUP_ID + " = ? AND "
                    + GroupMessageModel.COLUMN_OUTBOX + " = 0 AND "
                    + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0 "
                    + "ORDER BY " + GroupMessageModel.COLUMN_CREATED_AT + " ASC LIMIT 1",
                new String[]{
                    String.valueOf(groupDatabaseId)
                }
            );
        if (cursor != null && cursor.moveToFirst()) {
            return convert(cursor);
        }
        return null;
    }

    public GroupMessageModel getById(int id) {
        return getFirst(
            GroupMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(id)
            });
    }

    public GroupMessageModel getByUid(String uid) {
        return getFirst(
            GroupMessageModel.COLUMN_UID + "=?",
            new String[]{
                uid
            });
    }

    public List<GroupMessageModel> getAllRejectedMessagesInGroup(@NonNull GroupModel group) {
        return convertList(
            getReadableDatabase().query(
                getTableName(),
                null,
                GroupMessageModel.COLUMN_GROUP_ID + "=? AND " + MessageModel.COLUMN_STATE + "=?",
                new String[]{String.valueOf(group.getDatabaseId()), MessageState.FS_KEY_MISMATCH.toString()},
                null,
                null,
                null
            )
        );
    }

    public List<MessageModel> getMessagesByText(@Nullable String text, boolean includeArchived, boolean starredOnly, boolean sortAscending) {
        String displayClause, sortClause;
        if (starredOnly) {
            displayClause = " AND (" + GroupMessageModel.COLUMN_DISPLAY_TAGS + " & " + DISPLAY_TAG_STARRED + ") > 0 ";
        } else {
            displayClause = "";
        }

        if (sortAscending) {
            sortClause = " ASC ";
        } else {
            sortClause = " DESC ";
        }

        if (includeArchived) {
            if (text == null) {
                return convertAbstractList(getReadableDatabase().rawQuery(
                    "SELECT * FROM " + GroupMessageModel.TABLE +
                        " WHERE " + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0" +
                        displayClause +
                        " ORDER BY " + GroupMessageModel.COLUMN_CREATED_AT + sortClause +
                        "LIMIT 200",
                    new String[]{}));
            }

            return convertAbstractList(getReadableDatabase().rawQuery(
                "SELECT * FROM " + GroupMessageModel.TABLE +
                    " WHERE ( ( " + GroupMessageModel.COLUMN_BODY + " LIKE ? " +
                    " AND " + GroupMessageModel.COLUMN_TYPE + " IN (" +
                    MessageType.TEXT.serializedValue + "," +
                    MessageType.LOCATION.serializedValue + "," +
                    MessageType.POLL.serializedValue + ") )" +
                    " OR ( " + GroupMessageModel.COLUMN_CAPTION + " LIKE ? " +
                    " AND " + GroupMessageModel.COLUMN_TYPE + " = " +
                    MessageType.FILE.serializedValue + " ) )" +
                    " AND " + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0" +
                    displayClause +
                    " ORDER BY " + GroupMessageModel.COLUMN_CREATED_AT + sortClause +
                    "LIMIT 200",
                new String[]{
                    "%" + text + "%",
                    "%" + text + "%"
                }));
        } else {
            if (text == null) {
                return convertAbstractList(getReadableDatabase().rawQuery(
                    "SELECT * FROM " + GroupMessageModel.TABLE + " m" +
                        " INNER JOIN " + GroupModelOld.TABLE + " g ON g." + GroupModelOld.COLUMN_ID + " = m." + GroupMessageModel.COLUMN_GROUP_ID +
                        " WHERE g." + GroupModelOld.COLUMN_CONVERSATION_VISIBILITY + " != " + ConversationVisibility.ARCHIVED.getSerializedValue() +
                        " AND m." + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0" +
                        displayClause +
                        " ORDER BY m." + GroupMessageModel.COLUMN_CREATED_AT + sortClause +
                        "LIMIT 200",
                    new String[]{}));
            }

            return convertAbstractList(getReadableDatabase().rawQuery(
                "SELECT * FROM " + GroupMessageModel.TABLE + " m" +
                    " INNER JOIN " + GroupModelOld.TABLE + " g ON g." + GroupModelOld.COLUMN_ID + " = m." + GroupMessageModel.COLUMN_GROUP_ID +
                    " WHERE g." + GroupModelOld.COLUMN_CONVERSATION_VISIBILITY + " != " + ConversationVisibility.ARCHIVED.getSerializedValue() +
                    " AND ( ( m." + GroupMessageModel.COLUMN_BODY + " LIKE ? " +
                    " AND m." + GroupMessageModel.COLUMN_TYPE + " IN (" +
                    MessageType.TEXT.serializedValue + "," +
                    MessageType.LOCATION.serializedValue + "," +
                    MessageType.POLL.serializedValue + ") )" +
                    " OR ( m." + GroupMessageModel.COLUMN_CAPTION + " LIKE ? " +
                    " AND m." + GroupMessageModel.COLUMN_TYPE + " = " +
                    MessageType.FILE.serializedValue + " ) )" +
                    " AND m." + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0" +
                    displayClause +
                    " ORDER BY m." + GroupMessageModel.COLUMN_CREATED_AT + sortClause +
                    "LIMIT 200",
                new String[]{
                    "%" + text + "%",
                    "%" + text + "%"
                }));
        }
    }

    /**
     * Convert a cursor's rows to a list of {@link MessageModel}s.
     * Note that the cursor will be closed after conversion.
     */
    @NonNull
    private List<MessageModel> convertAbstractList(Cursor cursor) {
        List<MessageModel> result = new ArrayList<>();
        if (cursor != null) {
            try (cursor) {
                while (cursor.moveToNext()) {
                    result.add(convert(cursor));
                }
            }
        }
        return result;
    }

    /**
     * Convert a cursor's rows to a list of {@link GroupMessageModel}s.
     * Note that the cursor will be closed after conversion.
     */
    @NonNull
    private List<GroupMessageModel> convertList(@Nullable Cursor cursor) {
        List<GroupMessageModel> result = new ArrayList<>();
        if (cursor != null) {
            try (cursor) {
                while (cursor.moveToNext()) {
                    result.add(convert(cursor));
                }
            }
        }
        return result;
    }

    @Nullable
    private GroupMessageModel convert(@Nullable Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final GroupMessageModel groupMessageModel = new GroupMessageModel();

            //convert default
            super.convert(groupMessageModel, new CursorHelper(cursor, getColumnIndexCache()).current((CursorHelper.Callback) cursorHelper -> {
                int groupId = Objects.requireNonNull(cursorHelper.getInt(GroupMessageModel.COLUMN_GROUP_ID));
                groupMessageModel.setGroupId(groupId);
                String messageStates = cursorHelper.getString(GroupMessageModel.COLUMN_GROUP_MESSAGE_STATES);
                if (messageStates != null) {
                    try {
                        Map<String, String> messageStatesMap = parseJsonObjectAsStringMap(messageStates);
                        groupMessageModel.setGroupMessageStates(messageStatesMap);
                    } catch (Exception ignored) {
                        // map may not be available or empty
                        groupMessageModel.setGroupMessageStates(null);
                    }
                }
                return false;
            }));

            return groupMessageModel;
        }

        return null;
    }

    public long countMessages(int groupId) {
        return readCountQueryResult(getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + this.getTableName()
                + " WHERE " + GroupMessageModel.COLUMN_GROUP_ID + "=?",
            new String[]{
                String.valueOf(groupId)
            }
        ));
    }

    public long countUnreadMessages(int groupId) {
        return readCountQueryResult(getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + this.getTableName()
                + " WHERE " + GroupMessageModel.COLUMN_GROUP_ID + "=?"
                + " AND " + GroupMessageModel.COLUMN_OUTBOX + "=0"
                + " AND " + GroupMessageModel.COLUMN_IS_SAVED + "=1"
                + " AND " + GroupMessageModel.COLUMN_IS_READ + "=0"
                + " AND " + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + "=0",
            new String[]{
                String.valueOf(groupId)
            }
        ));
    }

    public List<GroupMessageModel> getUnreadMessages(int groupId) {
        return convertList(getReadableDatabase().query(this.getTableName(),
            null,
            GroupMessageModel.COLUMN_GROUP_ID + "=?"
                + " AND " + GroupMessageModel.COLUMN_OUTBOX + "=0"
                + " AND " + GroupMessageModel.COLUMN_IS_SAVED + "=1"
                + " AND " + GroupMessageModel.COLUMN_IS_READ + "=0"
                + " AND " + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + "=0",
            new String[]{
                String.valueOf(groupId)
            },
            null,
            null,
            null));
    }

    public long countByTypes(@NonNull MessageType[] messageTypes) {
        String[] args = new String[messageTypes.length];
        for (int n = 0; n < messageTypes.length; n++) {
            args[n] = String.valueOf(messageTypes[n].serializedValue);
        }

        Cursor cursor = getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + this.getTableName()
                + " WHERE " + GroupMessageModel.COLUMN_TYPE + " IN (" + makePlaceholders(args.length) + ")",
            args
        );
        return readCountQueryResult(cursor);
    }

    /**
     * Inserts or updates the message model into the database. Note that if the message is inserted,
     * the id of the message model will be updated.
     */
    public void createOrUpdate(@NonNull GroupMessageModel groupMessageModel) {
        boolean insert = true;
        if (groupMessageModel.getId() > 0) {
            insert = !DatabaseExtensionsKt.exists(
                getReadableDatabase(),
                getTableName(),
                GroupMessageModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(groupMessageModel.getId())
                }
            );
        }

        if (insert) {
            create(groupMessageModel);
        } else {
            update(groupMessageModel);
        }
    }

    /**
     * Inserts the message model into the database. Note that the id of the message model will be updated.
     */
    public void create(GroupMessageModel groupMessageModel) {
        ContentValues contentValues = this.buildContentValues(groupMessageModel);
        contentValues.put(GroupMessageModel.COLUMN_GROUP_ID, groupMessageModel.getGroupId());
        addGroupMessageStates(contentValues, groupMessageModel);
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        groupMessageModel.setId((int) newId);
    }

    /**
     * Inserts the message models into the database. Note that the ids of the message models will be updated.
     */
    public void create(@NonNull List<GroupMessageModel> groupMessageModels) {
        if (groupMessageModels.isEmpty()) {
            return;
        }
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            String tableName = getTableName();
            for (GroupMessageModel groupMessageModel : groupMessageModels) {
                ContentValues contentValues = buildContentValues(groupMessageModel);
                contentValues.put(GroupMessageModel.COLUMN_GROUP_ID, groupMessageModel.getGroupId());
                addGroupMessageStates(contentValues, groupMessageModel);
                long id = database.insertOrThrow(tableName, null, contentValues);
                groupMessageModel.setId((int) id);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public void update(GroupMessageModel groupMessageModel) {
        ContentValues contentValues = this.buildContentValues(groupMessageModel);
        addGroupMessageStates(contentValues, groupMessageModel);
        getWritableDatabase().update(
            this.getTableName(),
            contentValues,
            GroupMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(groupMessageModel.getId()),
            }
        );
    }

    public List<GroupMessageModel> find(int groupId, MessageService.MessageFilter filter) {
        QueryBuilder queryBuilder = new QueryBuilder();

        //sort by id!
        String orderBy = GroupMessageModel.COLUMN_ID + " DESC";
        List<String> placeholders = new ArrayList<>();

        queryBuilder.appendWhere(GroupMessageModel.COLUMN_GROUP_ID + "=?");
        placeholders.add(String.valueOf(groupId));

        //default filters
        this.appendFilter(queryBuilder, filter, placeholders);

        queryBuilder.setTables(this.getTableName());
        List<GroupMessageModel> messageModels = convertList(queryBuilder.query(
            getReadableDatabase(),
            null,
            null,
            placeholders.toArray(new String[0]),
            null,
            null,
            orderBy,
            this.limitFilter(filter)));

        this.postFilter(messageModels, filter);

        return messageModels;
    }

    public ChunkedSequence<GroupMessageModel> getByGroupId(long groupId) {
        long count;
        try (Cursor cursor = getReadableDatabase().query(
            "SELECT COUNT(*) FROM `" + getTableName() + "` WHERE `" + GroupMessageModel.COLUMN_GROUP_ID + "` = ?",
            new String[]{String.valueOf(groupId)}
        )) {
            if (cursor.moveToNext()) {
                count = cursor.getLong(0);
            } else {
                count = 0;
            }
        }

        return new ChunkedSequence<>(
            LongProgression.Companion.fromClosedRange(0, count, 10000),
            (from, size) ->
                getReadableDatabase().query(
                    "SELECT * FROM `" + getTableName() + "` WHERE `" + GroupMessageModel.COLUMN_GROUP_ID + "` = ? LIMIT ? OFFSET ?",
                    new String[]{String.valueOf(groupId), String.valueOf(size), String.valueOf(from)}
                ),
            this::convert
        );
    }

    /**
     * Read message models from the database into the given list.
     *
     * @param messageModels the list where the message models are appended to
     * @param count         the number of message models that are read
     * @param offset        the offset where the message models are started to be read
     */
    public void loadMessageModelsIntoList(List<GroupMessageModel> messageModels, int count, int offset) {
        String query = "SELECT * FROM `" + getTableName() + "` LIMIT ? OFFSET ?";
        String[] queryArgs = new String[]{String.valueOf(count), String.valueOf(offset)};
        try (Cursor cursor = getReadableDatabase().query(query, queryArgs)) {
            while (cursor.moveToNext()) {
                messageModels.add(convert(cursor));
            }
        }
    }

    public int delete(@NonNull GroupMessageModel groupMessageModel) {
        return getWritableDatabase().delete(
            this.getTableName(),
            GroupMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(groupMessageModel.getId())
            }
        );
    }

    public int deleteByGroupId(long groupId) {
        return getWritableDatabase().delete(
            this.getTableName(),
            GroupMessageModel.COLUMN_GROUP_ID + "=?",
            new String[]{
                String.valueOf(groupId)
            }
        );
    }

    @Nullable
    private GroupMessageModel getFirst(String selection, String[] selectionArgs) {
        Cursor cursor = getReadableDatabase().query(this.getTableName(), null, selection, selectionArgs, null, null, null);
        if (cursor != null) {
            try (cursor) {
                if (cursor.moveToFirst()) {
                    return convert(cursor);
                }
            }
        }
        return null;
    }

    private void addGroupMessageStates(@NonNull ContentValues contentValues, @NonNull GroupMessageModel groupMessageModel) {
        String groupMessageStates = null;
        if (groupMessageModel.getGroupMessageStates() != null) {
            groupMessageStates = new JSONObject(groupMessageModel.getGroupMessageStates()).toString();
        }

        contentValues.put(GroupMessageModel.COLUMN_GROUP_MESSAGE_STATES, groupMessageStates);
    }

    /**
     * Returns {@code true} if any of the given messages exist.
     * <p>
     * Messages are checked in batches of at most {@link SqliteDatabaseBackend#MAX_VARIABLE_NUMBER} / 2 to stay within SQLite's host-parameter limit.
     * <p>
     * Query:
     * <pre>{@code
     * SELECT EXISTS (
     *   SELECT 1 FROM m_group_message
     *    WHERE (apiMessageId, groupId) IN ((?, ?), (?, ?), ...)
     *    LIMIT 1
     * )
     * }</pre>
     */
    public boolean anyExists(@NonNull Set<GroupMessageIdentifier> groupMessageIdentifiers) {
        if (groupMessageIdentifiers.isEmpty()) {
            return false;
        }
        // Copy to a java.util.List to have the subList function available for chunking
        final List<GroupMessageIdentifier> messageIdentifiersList = new ArrayList<>(groupMessageIdentifiers);
        final int chunkSize = SqliteDatabaseBackend.MAX_VARIABLE_NUMBER / 2;
        for (int start = 0; start < messageIdentifiersList.size(); start += chunkSize) {
            final List<GroupMessageIdentifier> messageIdentifiersChunk = messageIdentifiersList.subList(
                start,
                Math.min(start + chunkSize, messageIdentifiersList.size())
            );
            final String placeholders = messageIdentifiersChunk.stream()
                .map(identifier -> "(?, ?)")
                .collect(Collectors.joining(", "));
            final String sql = "SELECT EXISTS(SELECT 1 FROM " + getTableName() + " WHERE ("
                + GroupMessageModel.COLUMN_API_MESSAGE_ID + ", " + GroupMessageModel.COLUMN_GROUP_ID + ") IN ("
                + placeholders + ") LIMIT 1)";
            final List<Object> bindArgs = new ArrayList<>(messageIdentifiersChunk.size() * 2);
            for (GroupMessageIdentifier messageIdentifier : messageIdentifiersChunk) {
                bindArgs.add(messageIdentifier.getMessageId().toString());
                bindArgs.add(messageIdentifier.getGroupDatabaseId());
            }
            final Cursor cursor = getReadableDatabase().query(sql, bindArgs.toArray());
            try (cursor) {
                final boolean exists = cursor.moveToFirst() && cursor.getInt(0) == 1;
                if (exists) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class Creator implements DatabaseCreationProvider {
        @Override
        @NonNull
        public String[] getCreationStatements() {
            return new String[]{
                "CREATE TABLE `" + GroupMessageModel.TABLE + "`" +
                    "(" +
                    "`" + GroupMessageModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT , " +
                    "`" + GroupMessageModel.COLUMN_UID + "` VARCHAR , " +
                    "`" + GroupMessageModel.COLUMN_API_MESSAGE_ID + "` VARCHAR , " +
                    "`" + GroupMessageModel.COLUMN_GROUP_ID + "` INTEGER NOT NULL , " +
                    "`" + GroupMessageModel.COLUMN_IDENTITY + "` VARCHAR , " +
                    "`" + GroupMessageModel.COLUMN_OUTBOX + "` SMALLINT , " +
                    "`" + GroupMessageModel.COLUMN_TYPE + "` INTEGER ," +
                    "`" + GroupMessageModel.COLUMN_CORRELATION_ID + "` VARCHAR ," +
                    "`" + GroupMessageModel.COLUMN_BODY + "` VARCHAR ," +
                    "`" + GroupMessageModel.COLUMN_CAPTION + "` VARCHAR ," +
                    "`" + GroupMessageModel.COLUMN_IS_READ + "` SMALLINT ," +
                    "`" + GroupMessageModel.COLUMN_IS_SAVED + "` SMALLINT ," +
                    "`" + GroupMessageModel.COLUMN_IS_QUEUED + "` TINYINT ," +
                    "`" + GroupMessageModel.COLUMN_STATE + "` VARCHAR , " +
                    "`" + GroupMessageModel.COLUMN_POSTED_AT + "` BIGINT , " +
                    "`" + GroupMessageModel.COLUMN_CREATED_AT + "` BIGINT , " +
                    "`" + GroupMessageModel.COLUMN_MODIFIED_AT + "` BIGINT , " +
                    "`" + GroupMessageModel.COLUMN_IS_STATUS_MESSAGE + "` SMALLINT ," +
                    "`" + GroupMessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID + "` VARCHAR ," +
                    "`" + GroupMessageModel.COLUMN_MESSAGE_CONTENTS_TYPE + "` TINYINT ," +
                    "`" + GroupMessageModel.COLUMN_MESSAGE_FLAGS + "` INT ," +
                    "`" + GroupMessageModel.COLUMN_DELIVERED_AT + "` DATETIME ," +
                    "`" + GroupMessageModel.COLUMN_READ_AT + "` DATETIME ," +
                    "`" + GroupMessageModel.COLUMN_FORWARD_SECURITY_MODE + "` TINYINT DEFAULT 0 ," +
                    "`" + GroupMessageModel.COLUMN_GROUP_MESSAGE_STATES + "` VARCHAR ," +
                    "`" + GroupMessageModel.COLUMN_DISPLAY_TAGS + "` TINYINT DEFAULT 0 ," +
                    "`" + GroupMessageModel.COLUMN_EDITED_AT + "` DATETIME ," +
                    "`" + GroupMessageModel.COLUMN_DELETED_AT + "` DATETIME );",

                // indices
                "CREATE INDEX `group_message_uid_idx` ON `" + GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_UID + "` )",
                "CREATE INDEX `m_group_message_outbox_idx` ON `" + GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_OUTBOX + "` );",
                "CREATE INDEX `m_group_message_identity_idx` ON `" + GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_IDENTITY + "` );",
                "CREATE INDEX `m_group_message_groupId_idx` ON `" + GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_GROUP_ID + "` );",
                "CREATE INDEX `groupMessageApiMessageIdIdx` ON `" + GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_API_MESSAGE_ID + "` );",
                "CREATE INDEX `groupMessageCorrelationIdIdx` ON `" + GroupMessageModel.TABLE + "` ( `" + GroupMessageModel.COLUMN_CORRELATION_ID + "` );",
                "CREATE INDEX `group_message_state_idx` ON `" + GroupMessageModel.TABLE
                    + "`(`" + MessageModel.COLUMN_TYPE
                    + "`, `" + MessageModel.COLUMN_STATE
                    + "`, `" + MessageModel.COLUMN_OUTBOX
                    + "`)",
            };
        }
    }
}
