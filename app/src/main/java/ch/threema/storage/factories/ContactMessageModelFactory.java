package ch.threema.storage.factories;

import static ch.threema.android.CursorExtensionsKt.readCountQueryResult;
import static ch.threema.common.DatabaseHelpers.makePlaceholders;
import static ch.threema.storage.models.data.DisplayTag.DISPLAY_TAG_STARRED;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ch.threema.app.services.MessageService;
import ch.threema.data.datatypes.ContactMessageIdentifier;
import ch.threema.data.datatypes.ConversationVisibility;
import ch.threema.data.storage.SqliteDatabaseBackend;
import ch.threema.domain.models.MessageId;
import ch.threema.storage.ChunkedSequence;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseExtensionsKt;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ContactMessageModel;
import ch.threema.storage.models.MessageType;
import kotlin.ranges.LongProgression;

public class ContactMessageModelFactory extends MessageModelFactory {
    public ContactMessageModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, ContactMessageModel.TABLE);
    }

    public List<ContactMessageModel> getAll() {
        return convertList(getReadableDatabase().query(getTableName(), null, null, null, null, null, null));
    }

    public ContactMessageModel getByApiMessageIdAndIdentity(@NonNull MessageId apiMessageId, String identity) {
        return getFirst(
            ContactMessageModel.COLUMN_API_MESSAGE_ID + "=? AND " + ContactMessageModel.COLUMN_IDENTITY + "=?",
            new String[]{
                apiMessageId.toString(),
                identity
            }
        );
    }

    public ContactMessageModel getByApiMessageIdAndIdentityAndIsOutbox(
        @NonNull MessageId apiMessageId,
        @NonNull String recipientIdentity,
        boolean isOutbox
    ) {
        return getFirst(
            ContactMessageModel.COLUMN_API_MESSAGE_ID + "=?"
                + " AND " + ContactMessageModel.COLUMN_IDENTITY + "=?"
                + " AND " + ContactMessageModel.COLUMN_OUTBOX + "=?",
            new String[]{
                apiMessageId.toString(),
                recipientIdentity,
                isOutbox ? "1" : "0"
            });
    }

    public ContactMessageModel getById(int id) {
        return getFirst(
            ContactMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(id)
            }
        );
    }

    public ContactMessageModel getByUid(String uid) {
        return getFirst(
            ContactMessageModel.COLUMN_UID + "=?",
            new String[]{
                uid
            }
        );
    }

    public List<MessageModel> getMessagesByText(@Nullable String text, boolean includeArchived, boolean starredOnly, boolean sortAscending) {
        String displayClause, sortClause;
        if (starredOnly) {
            displayClause = " AND (" + ContactMessageModel.COLUMN_DISPLAY_TAGS + " & " + DISPLAY_TAG_STARRED + ") > 0 ";
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
                    "SELECT * FROM " + ContactMessageModel.TABLE +
                        " WHERE " + ContactMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0" +
                        displayClause +
                        " ORDER BY " + ContactMessageModel.COLUMN_CREATED_AT + sortClause +
                        "LIMIT 200",
                    new String[]{}));
            }

            return convertAbstractList(getReadableDatabase().rawQuery(
                "SELECT * FROM " + ContactMessageModel.TABLE +
                    " WHERE ( ( " + ContactMessageModel.COLUMN_BODY + " LIKE ? " +
                    " AND " + ContactMessageModel.COLUMN_TYPE + " IN (" +
                    MessageType.TEXT.serializedValue + "," +
                    MessageType.LOCATION.serializedValue + "," +
                    MessageType.POLL.serializedValue + ") )" +
                    " OR ( " + ContactMessageModel.COLUMN_CAPTION + " LIKE ? " +
                    " AND " + ContactMessageModel.COLUMN_TYPE + " = " +
                    MessageType.FILE.serializedValue + " ) )" +
                    " AND " + ContactMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0" +
                    displayClause +
                    " ORDER BY " + ContactMessageModel.COLUMN_CREATED_AT + sortClause +
                    "LIMIT 200",
                new String[]{
                    "%" + text + "%",
                    "%" + text + "%"
                }));
        } else {
            if (text == null) {
                return convertAbstractList(getReadableDatabase().rawQuery(
                    "SELECT * FROM " + ContactMessageModel.TABLE + " m" +
                        " INNER JOIN " + ContactModel.TABLE + " c ON c." + ContactModel.COLUMN_IDENTITY + " = m." + ContactMessageModel.COLUMN_IDENTITY +
                        " WHERE c." + ContactModel.COLUMN_CONVERSATION_VISIBILITY + " != " + ConversationVisibility.ARCHIVED.getSerializedValue() +
                        " AND m." + ContactMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0" +
                        displayClause +
                        " ORDER BY m." + ContactMessageModel.COLUMN_CREATED_AT + sortClause +
                        "LIMIT 200",
                    new String[]{}));
            }
            return convertAbstractList(getReadableDatabase().rawQuery(
                "SELECT * FROM " + ContactMessageModel.TABLE + " m" +
                    " INNER JOIN " + ContactModel.TABLE + " c ON c." + ContactModel.COLUMN_IDENTITY + " = m." + ContactMessageModel.COLUMN_IDENTITY +
                    " WHERE c." + ContactModel.COLUMN_CONVERSATION_VISIBILITY + " != " + ConversationVisibility.ARCHIVED.getSerializedValue() +
                    " AND ( ( m." + ContactMessageModel.COLUMN_BODY + " LIKE ? " +
                    " AND m." + ContactMessageModel.COLUMN_TYPE + " IN (" +
                    MessageType.TEXT.serializedValue + "," +
                    MessageType.LOCATION.serializedValue + "," +
                    MessageType.POLL.serializedValue + ") )" +
                    " OR ( m." + ContactMessageModel.COLUMN_CAPTION + " LIKE ? " +
                    " AND m." + ContactMessageModel.COLUMN_TYPE + " = " +
                    MessageType.FILE.serializedValue + " ) )" +
                    " AND m." + ContactMessageModel.COLUMN_IS_STATUS_MESSAGE + " = 0" +
                    displayClause +
                    " ORDER BY m." + ContactMessageModel.COLUMN_CREATED_AT + sortClause +
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
     * Convert a cursor's rows to a list of {@link ContactMessageModel}s.
     * Note that the cursor will be closed after conversion.
     */
    @NonNull
    private List<ContactMessageModel> convertList(Cursor cursor) {
        List<ContactMessageModel> result = new ArrayList<>();
        if (cursor != null) {
            try (cursor) {
                while (cursor.moveToNext()) {
                    result.add(convert(cursor));
                }
            }
        }
        return result;
    }

    private ContactMessageModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            ContactMessageModel messageModel = new ContactMessageModel();
            //convert default
            super.convert(messageModel, new CursorHelper(cursor, getColumnIndexCache()));
            return messageModel;
        }
        return null;
    }

    public long countMessages(String identity) {
        return readCountQueryResult(getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + getTableName() + " WHERE " + ContactMessageModel.COLUMN_IDENTITY + "=?",
            new String[]{
                identity
            }
        ));
    }

    public long countUnreadMessages(String identity) {
        return readCountQueryResult(getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + getTableName()
                + " WHERE " + ContactMessageModel.COLUMN_IDENTITY + "=?"
                + " AND " + ContactMessageModel.COLUMN_OUTBOX + "=0"
                + " AND " + ContactMessageModel.COLUMN_IS_SAVED + "=1"
                + " AND " + ContactMessageModel.COLUMN_IS_READ + "=0"
                + " AND " + ContactMessageModel.COLUMN_IS_STATUS_MESSAGE + "=0",
            new String[]{
                identity
            }
        ));
    }


    public List<ContactMessageModel> getUnreadMessages(String identity) {
        return convertList(getReadableDatabase().query(
            getTableName(),
            null,
            ContactMessageModel.COLUMN_IDENTITY + "=?"
                + " AND " + ContactMessageModel.COLUMN_OUTBOX + "=0"
                + " AND " + ContactMessageModel.COLUMN_IS_SAVED + "=1"
                + " AND " + ContactMessageModel.COLUMN_IS_READ + "=0"
                + " AND " + ContactMessageModel.COLUMN_IS_STATUS_MESSAGE + "=0",
            new String[]{
                identity
            },
            null,
            null,
            null)
        );
    }

    public ContactMessageModel getLastMessage(String identity) {
        Cursor cursor = getReadableDatabase().query(
            getTableName(),
            null,
            ContactMessageModel.COLUMN_IDENTITY + "=?",
            new String[]{identity},
            null,
            null,
            ContactMessageModel.COLUMN_ID + " DESC",
            "1"
        );
        if (cursor != null) {
            try (cursor) {
                if (cursor.moveToFirst()) {
                    return convert(cursor);
                }
            }
        }
        return null;
    }

    public long countByTypes(@NonNull MessageType[] messageTypes) {
        String[] args = new String[messageTypes.length];
        for (int n = 0; n < messageTypes.length; n++) {
            args[n] = String.valueOf(messageTypes[n].serializedValue);
        }
        Cursor cursor = getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + getTableName()
                + " WHERE " + ContactMessageModel.COLUMN_TYPE + " IN (" + makePlaceholders(args.length) + ")",
            args
        );
        return readCountQueryResult(cursor);
    }

    /**
     * Inserts or updates the message model into the database. Note that if the message is inserted,
     * the id of the message model will be updated.
     */
    public void createOrUpdate(@NonNull ContactMessageModel messageModel) {
        boolean insert = true;
        if (messageModel.getId() > 0) {
            insert = !DatabaseExtensionsKt.exists(
                getReadableDatabase(),
                getTableName(),
                ContactMessageModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(messageModel.getId())
                }
            );
        }

        if (insert) {
            create(messageModel);
        } else {
            update(messageModel);
        }
    }

    /**
     * Inserts the message model into the database. Note that the id of the message model will be updated.
     */
    public void create(@NonNull ContactMessageModel messageModel) {
        ContentValues contentValues = this.buildContentValues(messageModel);
        long newId = getWritableDatabase().insertOrThrow(getTableName(), null, contentValues);
        messageModel.setId((int) newId);
    }

    /**
     * Inserts the message models into the database. Note that the ids of the message models will be updated.
     */
    public void create(@NonNull List<ContactMessageModel> messageModels) {
        if (messageModels.isEmpty()) {
            return;
        }
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            String tableName = getTableName();
            for (ContactMessageModel messageModel : messageModels) {
                long id = database.insertOrThrow(
                    tableName,
                    null,
                    buildContentValues(messageModel)
                );
                messageModel.setId((int) id);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public void update(@NonNull ContactMessageModel messageModel) {
        ContentValues contentValues = this.buildContentValues(messageModel);
        getWritableDatabase().update(getTableName(),
            contentValues,
            ContactMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(messageModel.getId())
            });
    }

    public int delete(@NonNull ContactMessageModel messageModel) {
        return getWritableDatabase().delete(
            getTableName(),
            ContactMessageModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(messageModel.getId())
            }
        );
    }

    public List<ContactMessageModel> find(String identity, MessageService.MessageFilter filter) {
        QueryBuilder queryBuilder = new QueryBuilder();

        //sort by id!
        String orderBy = ContactMessageModel.COLUMN_ID + " DESC";
        List<String> placeholders = new ArrayList<>();

        queryBuilder.appendWhere(ContactMessageModel.COLUMN_IDENTITY + "=?");
        placeholders.add(identity);

        //default filters
        this.appendFilter(queryBuilder, filter, placeholders);
        queryBuilder.setTables(getTableName());
        List<ContactMessageModel> messageModels = convertList(queryBuilder.query(
            getReadableDatabase(),
            null,
            null,
            placeholders.toArray(new String[0]),
            null,
            null,
            orderBy,
            this.limitFilter(filter))
        );

        this.postFilter(messageModels, filter);

        return messageModels;
    }

    /**
     * Check if there is a call with the given identity and call id within the latest calls.
     *
     * @param identity the identity of the call partner
     * @param callId   the call id
     * @param limit    the maximum number of latest calls
     * @return {@code true} if this call exists in the latest calls, {@code false} otherwise
     */
    public boolean hasVoipStatusForCallId(@NonNull String identity, long callId, int limit) {
        QueryBuilder queryBuilder = new QueryBuilder();

        String orderBy = MessageModel.COLUMN_CREATED_AT + " DESC";

        queryBuilder.appendWhere(MessageModel.COLUMN_IDENTITY + "=?");
        queryBuilder.appendWhere(MessageModel.COLUMN_TYPE + "=?");
        queryBuilder.setTables(getTableName());

        Cursor cursor = queryBuilder.query(getReadableDatabase(),
            null,
            null,
            new String[]{identity, String.valueOf(MessageType.VOIP_STATUS.serializedValue)},
            null,
            null,
            orderBy,
            String.valueOf(limit));

        if (cursor != null) {
            try (cursor) {
                List<ContactMessageModel> messageModels = convertList(cursor);
                for (ContactMessageModel messageModel : messageModels) {
                    if (messageModel.getVoipStatusData() != null && callId == messageModel.getVoipStatusData().getCallId()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public ChunkedSequence<ContactMessageModel> getByIdentity(String identity) {
        long count;
        try (Cursor cursor = getReadableDatabase().query(
            "SELECT COUNT(*) FROM `" + getTableName() + "` WHERE `" + ContactMessageModel.COLUMN_IDENTITY + "` = ?",
            new String[]{identity}
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
                    "SELECT * FROM `" + getTableName() + "` WHERE `" + ContactMessageModel.COLUMN_IDENTITY + "` = ? LIMIT ? OFFSET ?",
                    new String[]{identity, String.valueOf(size), String.valueOf(from)}
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
    public void loadMessageModelsIntoList(@NonNull List<ContactMessageModel> messageModels, int count, int offset) {
        String query = "SELECT * FROM `" + getTableName() + "` LIMIT ? OFFSET ?";
        String[] queryArgs = new String[]{String.valueOf(count), String.valueOf(offset)};
        try (Cursor cursor = getReadableDatabase().query(query, queryArgs)) {
            while (cursor.moveToNext()) {
                messageModels.add(convert(cursor));
            }
        }
    }

    /**
     * Returns {@code true} if any of the given messages exist.
     * <p>
     * Messages are checked in batches of at most {@link SqliteDatabaseBackend#MAX_VARIABLE_NUMBER} / 2 to stay within SQLite's host-parameter limit.
     * <p>
     * Query:
     * <pre>{@code
     * SELECT EXISTS (
     *   SELECT 1 FROM message
     *    WHERE (apiMessageId, identity) IN ((?, ?), (?, ?), ...)
     *    LIMIT 1
     * )
     * }</pre>
     */
    public boolean anyExists(@NonNull Set<ContactMessageIdentifier> contactMessageIdentifiers) {
        if (contactMessageIdentifiers.isEmpty()) {
            return false;
        }
        // Copy to a java.util.List to have the subList function available for chunking
        final List<ContactMessageIdentifier> messageIdentifiersList = new ArrayList<>(contactMessageIdentifiers);
        final int chunkSize = SqliteDatabaseBackend.MAX_VARIABLE_NUMBER / 2;
        for (int start = 0; start < messageIdentifiersList.size(); start += chunkSize) {
            final List<ContactMessageIdentifier> messageIdentifiersChunk = messageIdentifiersList.subList(
                start,
                Math.min(start + chunkSize, messageIdentifiersList.size())
            );
            final String placeholders = messageIdentifiersChunk.stream()
                .map(identifier -> "(?, ?)")
                .collect(Collectors.joining(", "));
            final String sql = "SELECT EXISTS (SELECT 1 FROM " + getTableName() + " WHERE ("
                + ContactMessageModel.COLUMN_API_MESSAGE_ID + ", " + ContactMessageModel.COLUMN_IDENTITY + ") IN ("
                + placeholders + ") LIMIT 1)";
            final List<Object> bindArgs = new ArrayList<>(messageIdentifiersChunk.size() * 2);
            for (ContactMessageIdentifier messageIdentifier : messageIdentifiersChunk) {
                bindArgs.add(messageIdentifier.getMessageId().toString());
                bindArgs.add(messageIdentifier.getIdentity());
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

    @Nullable
    private ContactMessageModel getFirst(String selection, String[] selectionArgs) {
        Cursor cursor = getReadableDatabase().query(getTableName(), null, selection, selectionArgs, null, null, null);
        try (cursor) {
            if (cursor != null && cursor.moveToFirst()) {
                return convert(cursor);
            }
        }
        return null;
    }

    public static class Creator implements DatabaseCreationProvider {
        @Override
        @NonNull
        public String[] getCreationStatements() {
            return new String[]{
                //create table
                "CREATE TABLE `" + ContactMessageModel.TABLE + "`(" +
                    "`" + ContactMessageModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT , " +
                    "`" + ContactMessageModel.COLUMN_UID + "` VARCHAR , " +
                    "`" + ContactMessageModel.COLUMN_API_MESSAGE_ID + "` VARCHAR , " +
                    "`" + ContactMessageModel.COLUMN_IDENTITY + "` VARCHAR , " +
                    //TODO(ANDR-XXXX): change to TINYINT
                    "`" + ContactMessageModel.COLUMN_OUTBOX + "` SMALLINT , " +
                    "`" + ContactMessageModel.COLUMN_TYPE + "` INTEGER , " +
                    "`" + ContactMessageModel.COLUMN_BODY + "` VARCHAR , " +
                    "`" + ContactMessageModel.COLUMN_CORRELATION_ID + "` VARCHAR , " +
                    "`" + ContactMessageModel.COLUMN_CAPTION + "` VARCHAR , " +
                    //TODO(ANDR-XXXX): change to TINYINT
                    "`" + ContactMessageModel.COLUMN_IS_READ + "` SMALLINT , " +
                    //TODO(ANDR-XXXX): change to TINYINT
                    "`" + ContactMessageModel.COLUMN_IS_SAVED + "` SMALLINT , " +
                    "`" + ContactMessageModel.COLUMN_IS_QUEUED + "` TINYINT , " +
                    "`" + ContactMessageModel.COLUMN_STATE + "` VARCHAR , " +
                    "`" + ContactMessageModel.COLUMN_POSTED_AT + "` BIGINT , " +
                    "`" + ContactMessageModel.COLUMN_CREATED_AT + "` BIGINT , " +
                    "`" + ContactMessageModel.COLUMN_MODIFIED_AT + "` BIGINT , " +
                    //TODO(ANDR-XXXX): change to TINYINT
                    "`" + ContactMessageModel.COLUMN_IS_STATUS_MESSAGE + "` SMALLINT ," +
                    "`" + ContactMessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID + "` VARCHAR ," +
                    "`" + ContactMessageModel.COLUMN_MESSAGE_CONTENTS_TYPE + "` TINYINT ," +
                    "`" + ContactMessageModel.COLUMN_MESSAGE_FLAGS + "` INT ," +
                    "`" + ContactMessageModel.COLUMN_DELIVERED_AT + "` DATETIME ," +
                    "`" + ContactMessageModel.COLUMN_READ_AT + "` DATETIME ," +
                    "`" + ContactMessageModel.COLUMN_FORWARD_SECURITY_MODE + "` TINYINT DEFAULT 0 ," +
                    "`" + ContactMessageModel.COLUMN_DISPLAY_TAGS + "` TINYINT DEFAULT 0 ," +
                    "`" + ContactMessageModel.COLUMN_EDITED_AT + "` DATETIME ," +
                    "`" + ContactMessageModel.COLUMN_DELETED_AT + "` DATETIME );",

                // indices
                "CREATE INDEX `contact_message_uid_idx` ON `" + ContactMessageModel.TABLE + "` ( `" + ContactMessageModel.COLUMN_UID + "` )",
                "CREATE INDEX `message_identity_idx` ON `" + ContactMessageModel.TABLE + "` ( `" + ContactMessageModel.COLUMN_IDENTITY + "` )",
                "CREATE INDEX `messageApiMessageIdIdx` ON `" + ContactMessageModel.TABLE + "` ( `" + ContactMessageModel.COLUMN_API_MESSAGE_ID + "` )",
                "CREATE INDEX `message_outbox_idx` ON `" + ContactMessageModel.TABLE + "` ( `" + ContactMessageModel.COLUMN_OUTBOX + "` )",
                "CREATE INDEX `messageCorrelationIdIx` ON `" + ContactMessageModel.TABLE + "` ( `" + ContactMessageModel.COLUMN_CORRELATION_ID + "` )",
                "CREATE INDEX `message_count_idx` ON `" + ContactMessageModel.TABLE
                    + "`(`" + ContactMessageModel.COLUMN_IDENTITY
                    + "`, `" + ContactMessageModel.COLUMN_OUTBOX
                    + "`, `" + ContactMessageModel.COLUMN_IS_SAVED
                    + "`, `" + ContactMessageModel.COLUMN_IS_READ
                    + "`, `" + ContactMessageModel.COLUMN_IS_STATUS_MESSAGE
                    + "`)",
                "CREATE INDEX `message_state_idx` ON `" + ContactMessageModel.TABLE
                    + "`(`" + MessageModel.COLUMN_TYPE
                    + "`, `" + MessageModel.COLUMN_STATE
                    + "`, `" + MessageModel.COLUMN_OUTBOX
                    + "`)"
            };
        }
    }
}
