package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;
import android.database.SQLException;

import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.datatypes.ConversationId;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.models.ConversationTag;
import ch.threema.storage.models.ConversationTagModel;

public class ConversationTagFactory extends ModelFactory {
    private static final Logger logger = getThreemaLogger("ConversationTagFactory");

    public ConversationTagFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, ConversationTagModel.TABLE);
    }

    @NonNull
    public List<ConversationTagModel> getAll() {
        try (Cursor cursor = getReadableDatabase().query(this.getTableName(), null, null, null, null, null, null)) {
            return convertList(cursor);
        }
    }

    public ConversationTagModel getByConversationIdAndTag(@NonNull ConversationId conversationId, @NonNull ConversationTag tag) {
        return getFirst(
            ConversationTagModel.COLUMN_CONVERSATION_UID + "=? AND " + ConversationTagModel.COLUMN_TAG + "=? ",
            new String[]{
                conversationId.toDatabaseValue(),
                tag.value
            }
        );
    }

    public long countByTag(@NonNull ConversationTag tag) {
        return DatabaseUtil.count(
            getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + this.getTableName() + " WHERE " + ConversationTagModel.COLUMN_TAG + "=?",
                new String[]{
                    tag.value
                }
            )
        );
    }

    @NonNull
    public List<ConversationId> getAllConversationIdsByTag(@NonNull ConversationTag tag) {
        try (Cursor cursor = getReadableDatabase().query(
            SupportSQLiteQueryBuilder.builder(getTableName())
                .columns(new String[]{ConversationTagModel.COLUMN_CONVERSATION_UID})
                .selection(ConversationTagModel.COLUMN_TAG + " = ?", new String[]{tag.value})
                .create()
        )) {
            final @NonNull List<ConversationId> conversationIds = new ArrayList<>(cursor.getCount());
            final int columnIndex = cursor.getColumnIndexOrThrow(ConversationTagModel.COLUMN_CONVERSATION_UID);
            while (cursor.moveToNext()) {
                final @Nullable String conversationIdDatabaseValue = cursor.getString(columnIndex);
                if (conversationIdDatabaseValue != null) {
                    final @Nullable ConversationId conversationId = ConversationId.fromDatabaseValue(conversationIdDatabaseValue);
                    if (conversationId != null) {
                        conversationIds.add(conversationId);
                    }
                }
            }
            return conversationIds;
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Could not get conversation ids by tag '{}'", tag, e);
            return List.of();
        }
    }

    @NonNull
    private List<ConversationTagModel> convertList(@Nullable Cursor cursor) {
        if (cursor == null) {
            return new ArrayList<>();
        }
        final List<ConversationTagModel> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(convert(cursor));
        }
        return result;
    }

    @Nullable
    private ConversationTagModel convert(@Nullable Cursor cursor) {
        if (cursor == null || cursor.getPosition() < 0) {
            return null;
        }
        final @NonNull AtomicReference<ConversationTagModel> conversationTagModel = new AtomicReference<>();
        new CursorHelper(cursor, getColumnIndexCache())
            .current(
                (CursorHelper.Callback) cursorHelper -> {
                    final @Nullable String conversationIdDatabaseValue = cursorHelper.getString(ConversationTagModel.COLUMN_CONVERSATION_UID);
                    if (conversationIdDatabaseValue == null) {
                        return false;
                    }
                    final @Nullable ConversationId conversationId = ConversationId.fromDatabaseValue(conversationIdDatabaseValue);
                    if (conversationId == null) {
                        return false;
                    }
                    final @Nullable String tag = cursorHelper.getString(ConversationTagModel.COLUMN_TAG);
                    final @Nullable Instant createdAt = cursorHelper.getInstant(ConversationTagModel.COLUMN_CREATED_AT);
                    conversationTagModel.set(new ConversationTagModel(conversationId, tag, createdAt));
                    return false;
                }
            );
        return conversationTagModel.get();
    }

    @NonNull
    private ContentValues buildContentValues(@NonNull ConversationTagModel conversationTagModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(ConversationTagModel.COLUMN_CONVERSATION_UID, conversationTagModel.getConversationId().toDatabaseValue());
        contentValues.put(ConversationTagModel.COLUMN_TAG, conversationTagModel.getTag());
        contentValues.put(
            ConversationTagModel.COLUMN_CREATED_AT,
            conversationTagModel.getCreatedAt() != null
                ? conversationTagModel.getCreatedAt().toEpochMilli()
                : null
        );
        return contentValues;
    }

    public void create(@NonNull ConversationTagModel conversationTagModel) {
        logger.debug("create conversation tag {} {}", conversationTagModel.getConversationId(), conversationTagModel.getTag());
        ContentValues contentValues = buildContentValues(conversationTagModel);
        getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
    }

    public void deleteByConversationIdAndTag(@NonNull ConversationId conversationId, @NonNull ConversationTag tag) {
        deleteByConversationIdAndTag(conversationId, tag.value);
    }

    public void deleteByConversationIdAndTag(@NonNull ConversationId conversationId, @NonNull String tag) {
        getWritableDatabase().delete(
            this.getTableName(),
            ConversationTagModel.COLUMN_CONVERSATION_UID + "=? AND " + ConversationTagModel.COLUMN_TAG + "=? ",
            new String[]{
                conversationId.toDatabaseValue(),
                tag
            }
        );
    }

    public void deleteByConversationId(@NonNull ConversationId conversationId) {
        getWritableDatabase().delete(
            this.getTableName(),
            ConversationTagModel.COLUMN_CONVERSATION_UID + "=?",
            new String[]{
                conversationId.toDatabaseValue()
            }
        );
    }

    @Nullable
    private ConversationTagModel getFirst(String selection, String[] selectionArgs) {
        Cursor cursor = getReadableDatabase().query(
            this.getTableName(),
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
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

    public static class Creator implements DatabaseCreationProvider {
        @Override
        @NonNull
        public String[] getCreationStatements() {
            return new String[]{
                "CREATE TABLE IF NOT EXISTS `" + ConversationTagModel.TABLE + "` (" +
                    "`" + ConversationTagModel.COLUMN_CONVERSATION_UID + "` VARCHAR NOT NULL, " +
                    "`" + ConversationTagModel.COLUMN_TAG + "` BLOB NULL," +
                    "`" + ConversationTagModel.COLUMN_CREATED_AT + "` BIGINT, " +
                    "PRIMARY KEY (`" + ConversationTagModel.COLUMN_CONVERSATION_UID + "`, `" + ConversationTagModel.COLUMN_TAG + "`) " +
                    ");",

                "CREATE UNIQUE INDEX IF NOT EXISTS `conversationTagKeyConversationTag` ON `" + ConversationTagModel.TABLE
                    + "` ( `" + ConversationTagModel.COLUMN_CONVERSATION_UID + "`, `" + ConversationTagModel.COLUMN_TAG + "` );",
                "CREATE INDEX IF NOT EXISTS `conversationTagConversation` ON `" + ConversationTagModel.TABLE + "` ( `" + ConversationTagModel.COLUMN_CONVERSATION_UID + "` );",
                "CREATE INDEX IF NOT EXISTS`conversationTagTag` ON `" + ConversationTagModel.TABLE + "` ( `" + ConversationTagModel.COLUMN_TAG + "` );"
            };
        }
    }

}
