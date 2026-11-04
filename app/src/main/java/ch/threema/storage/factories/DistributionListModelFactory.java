package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.DistributionListService;
import ch.threema.data.datatypes.ConversationCategory;
import ch.threema.data.datatypes.ConversationVisibility;
import ch.threema.data.storage.SqliteDatabaseBackend;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.DistributionListModel;

import static ch.threema.common.DatabaseHelpers.convertToSelectionArgs;
import static ch.threema.common.DatabaseHelpers.makePlaceholders;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.logging.ErrorReportingKt.logAndReportError;

public class DistributionListModelFactory extends ModelFactory {
    private static final Logger logger = getThreemaLogger("DistributionListModelFactory");

    public DistributionListModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, DistributionListModel.TABLE);
    }

    @NonNull
    public List<DistributionListModel> getAll() {
        return convertList(
            getReadableDatabase().query(getTableName(), null, null, null, null, null, null)
        );
    }

    public DistributionListModel getById(long id) {
        return getFirstOrNull(
            DistributionListModel.COLUMN_ID + "=?",
            String.valueOf(id)
        );
    }

    /**
     * Checks whether the id belongs to an existing distribution list. Note that this also includes ad-hoc distribution lists.
     *
     * <pre>{@code
     * SELECT EXISTS(
     *     SELECT 1
     *     FROM distribution_list
     *     WHERE id = ?
     *     LIMIT 1
     * )
     * }</pre>
     *
     * @param id the id to check
     * @return {@code true} if the id exists
     */
    public boolean existsById(long id) {
        String query = "SELECT EXISTS(SELECT 1 FROM " + DistributionListModel.TABLE
            + " WHERE " + DistributionListModel.COLUMN_ID + " = ? LIMIT 1 )";

        try (Cursor cursor = getReadableDatabase().query(query, new String[]{String.valueOf(id)})) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    /**
     * Returns {@code true} if every id from {@code ids} exists in the database
     * <p>
     * Ids are checked in batches of at most {@link SqliteDatabaseBackend#MAX_VARIABLE_NUMBER} to stay within SQLite's host-parameter limit.
     *
     * <p>
     * Query:
     * <pre>{@code
     * SELECT COUNT(*)
     *   FROM distribution_list
     *  WHERE id IN (?, ?, ?, ...)
     * }</pre>
     */
    public boolean allExist(@NonNull Set<Long> ids) {
        if (ids.isEmpty()) {
            return true;
        }
        // Copy to a java.util.List to have the subList function available for chunking
        final List<Long> idsList = new ArrayList<>(ids);
        for (int start = 0; start < idsList.size(); start += SqliteDatabaseBackend.MAX_VARIABLE_NUMBER) {
            final List<Long> idsChunk = idsList.subList(
                start,
                Math.min(start + SqliteDatabaseBackend.MAX_VARIABLE_NUMBER, idsList.size())
            );
            final String placeholders = makePlaceholders(idsChunk.size());
            final String query = "SELECT COUNT(*) FROM " + DistributionListModel.TABLE
                + " WHERE " + DistributionListModel.COLUMN_ID + " IN (" + placeholders + ")";
            try (final Cursor cursor = getReadableDatabase().query(query, idsChunk.toArray())) {
                if (cursor.moveToFirst()) {
                    final long count = cursor.getLong(0);
                    if (count < idsChunk.size()) {
                        return false;
                    }
                } else {
                    // Cursor is empty
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * Checks whether at least one of the provided ids corresponds to an existing distribution list.
     *
     * <pre>{@code
     * SELECT EXISTS(
     *     SELECT 1
     *     FROM distribution_list
     *     WHERE id IN (?, ?, ..., ?)
     *     LIMIT 1
     * )
     * }</pre>
     * <p>
     * Ids are checked in batches of at most {@link SqliteDatabaseBackend#MAX_VARIABLE_NUMBER} to stay within SQLite's host-parameter limit. Returns
     * as soon as any batch finds a match.
     *
     * @param ids the ids to check
     * @return {@code true} if at least one id exists; {@code false} if {@code ids} is empty
     */
    public boolean existsAnyById(@NonNull Set<Long> ids) {
        if (ids.isEmpty()) {
            return false;
        }
        // Copy to a java.util.List to have the subList function available for chunking
        final List<Long> idList = new ArrayList<>(ids);
        for (int start = 0; start < idList.size(); start += SqliteDatabaseBackend.MAX_VARIABLE_NUMBER) {
            final List<Long> chunk = idList.subList(
                start,
                Math.min(start + SqliteDatabaseBackend.MAX_VARIABLE_NUMBER, idList.size())
            );
            if (chunkExistsAny(chunk)) {
                return true;
            }
        }
        return false;
    }

    private boolean chunkExistsAny(@NonNull List<Long> chunk) {
        String placeholders = makePlaceholders(chunk.size());

        String query = "SELECT EXISTS(SELECT 1 FROM " + getTableName()
            + " WHERE " + DistributionListModel.COLUMN_ID + " IN (" + placeholders + ")"
            + " LIMIT 1 )";

        try (Cursor cursor = getReadableDatabase().query(query, chunk.toArray())) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    @NonNull
    public List<DistributionListModel> getByIds(@NonNull List<Long> ids) {
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        final @NonNull String placeholders = makePlaceholders(ids.size());
        final @NonNull String[] selectionArgs = convertToSelectionArgs(ids);
        final @NonNull String selection = DistributionListModel.COLUMN_ID + " IN (" + placeholders + ")";
        return convertList(
            getReadableDatabase().query(getTableName(), null, selection, selectionArgs, null, null, null)
        );
    }

    @NonNull
    public List<DistributionListModel> getAllArchived() {
        return convertList(
            getReadableDatabase().query(
                "SELECT * FROM " + getTableName() + " WHERE " + DistributionListModel.COLUMN_CONVERSATION_VISIBILITY + " = ?",
                new Object[]{ConversationVisibility.ARCHIVED.getSerializedValue()}
            )
        );
    }

    @NonNull
    public Set<Long> getAllNonAdHocIds() {
        String query = "SELECT " + DistributionListModel.COLUMN_ID + " FROM " + getTableName() +
            " WHERE " + DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST + " != 1";

        Set<Long> ids = new HashSet<>();
        try (Cursor cursor = getReadableDatabase().query(query)) {
            while (cursor.moveToNext()) {
                ids.add(
                    cursor.getLong(cursor.getColumnIndexOrThrow(DistributionListModel.COLUMN_ID))
                );
            }
        }
        return ids;
    }

    @NonNull
    private List<DistributionListModel> convert(@NonNull QueryBuilder queryBuilder, @Nullable String orderBy) {
        queryBuilder.setTables(getTableName());
        return convertList(
            queryBuilder.query(getReadableDatabase(), null, null, null, null, null, orderBy)
        );
    }

    @NonNull
    private List<DistributionListModel> convertList(@Nullable Cursor cursor) {
        final @NonNull List<DistributionListModel> results = new ArrayList<>();
        if (cursor == null) {
            return results;
        }
        try (cursor) {
            while (cursor.moveToNext()) {
                final @Nullable DistributionListModel distributionListModel = convert(cursor);
                if (distributionListModel != null) {
                    results.add(distributionListModel);
                }
            }
        }
        return results;
    }

    @Nullable
    private DistributionListModel convert(@Nullable Cursor cursor) {
        if (cursor == null || cursor.getPosition() < 0) {
            return null;
        }
        final @NonNull DistributionListModel distributionListModel = new DistributionListModel();
        //convert default
        new CursorHelper(cursor, getColumnIndexCache()).current(
            (CursorHelper.Callback) cursorHelper -> {
                Integer conversationVisibilityValue = cursorHelper.getInt(DistributionListModel.COLUMN_CONVERSATION_VISIBILITY);
                if (conversationVisibilityValue == null) {
                    throw new IllegalStateException("Conversation visibility value is null");
                }
                ConversationVisibility conversationVisibility = ConversationVisibility.deserialize(conversationVisibilityValue);
                if (conversationVisibility == null) {
                    logAndReportError(logger, "Could not read conversation visibility with value {}", conversationVisibilityValue);
                    conversationVisibility = ConversationVisibility.NORMAL;
                }
                Integer conversationCategoryValue = cursorHelper.getInt(DistributionListModel.COLUMN_CONVERSATION_CATEGORY);
                if (conversationCategoryValue == null) {
                    throw new IllegalStateException("Conversation category value is null");
                }
                ConversationCategory conversationCategory = ConversationCategory.deserialize(conversationCategoryValue);
                if (conversationCategory == null) {
                    logAndReportError(logger, "Could not read conversation category with value {}", conversationCategoryValue);
                    conversationCategory = ConversationCategory.DEFAULT;
                }

                distributionListModel
                    .setId(cursorHelper.getLong(DistributionListModel.COLUMN_ID))
                    .setName(cursorHelper.getString(DistributionListModel.COLUMN_NAME))
                    .setCreatedAt(cursorHelper.getInstant(DistributionListModel.COLUMN_CREATED_AT))
                    .setLastUpdate(cursorHelper.getInstant(DistributionListModel.COLUMN_LAST_UPDATE))
                    .setConversationVisibility(conversationVisibility)
                    .setConversationCategory(conversationCategory)
                    .setAdHocDistributionList(cursorHelper.getBoolean(DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST));

                return false;
            }
        );
        return distributionListModel;
    }

    public void createOrUpdate(@NonNull DistributionListModel distributionListModel) {
        boolean insert = true;
        if (distributionListModel.getId() > 0) {
            Cursor cursor = getReadableDatabase().query(
                this.getTableName(),
                null,
                DistributionListModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(distributionListModel.getId())
                },
                null,
                null,
                null
            );
            if (cursor != null) {
                try (cursor) {
                    insert = !cursor.moveToNext();
                }
            }
        }

        if (insert) {
            create(distributionListModel);
        } else {
            update(distributionListModel);
        }
    }

    @NonNull
    private ContentValues buildContentValues(@NonNull DistributionListModel distributionListModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DistributionListModel.COLUMN_NAME, distributionListModel.getName());
        contentValues.put(DistributionListModel.COLUMN_CREATED_AT, distributionListModel.getCreatedAt() != null ? distributionListModel.getCreatedAt().toEpochMilli() : null);
        contentValues.put(DistributionListModel.COLUMN_LAST_UPDATE, distributionListModel.getLastUpdate() != null ? distributionListModel.getLastUpdate().toEpochMilli() : null);
        contentValues.put(DistributionListModel.COLUMN_CONVERSATION_VISIBILITY, distributionListModel.getConversationVisibility().getSerializedValue());
        contentValues.put(DistributionListModel.COLUMN_CONVERSATION_CATEGORY, distributionListModel.getConversationCategory().getSerializedValue());
        contentValues.put(DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST, distributionListModel.isAdHocDistributionList());
        return contentValues;
    }

    /**
     * Create a new distribution list model. If the ID of the given model is <= 0 or already used in
     * the database, a random ID is chosen and the model is updated accordingly.
     *
     * @param distributionListModel the distribution list model that is inserted into the database
     */
    public void create(@NonNull DistributionListModel distributionListModel) {
        ContentValues contentValues = buildContentValues(distributionListModel);

        long distributionListId = distributionListModel.getId();

        if (distributionListId <= 0 || doesIdExist(distributionListId)) {
            distributionListId = getUniqueId();
        }

        contentValues.put(DistributionListModel.COLUMN_ID, distributionListId);

        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        distributionListModel.setId(newId);
    }

    /**
     * Persist the given distribution list model.
     *
     * @throws android.database.SQLException if a distribution list with the given id already exists
     */
    public void persist(@NonNull DistributionListModel distributionListModel) {
        ContentValues contentValues = buildContentValues(distributionListModel);
        contentValues.put(DistributionListModel.COLUMN_ID, distributionListModel.getId());

        getWritableDatabase().insertOrThrow(getTableName(), null, contentValues);
    }

    public void update(@NonNull DistributionListModel distributionListModel) {
        ContentValues contentValues = buildContentValues(distributionListModel);
        getWritableDatabase().update(
            getTableName(),
            contentValues,
            DistributionListModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListModel.getId())
            }
        );
    }

    public int delete(@NonNull DistributionListModel distributionListModel) {
        return getWritableDatabase().delete(
            getTableName(),
            DistributionListModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(distributionListModel.getId())
            }
        );
    }

    @Nullable
    private DistributionListModel getFirstOrNull(@Nullable String selection, @Nullable String... selectionArgs) {
        final @Nullable Cursor cursor = getReadableDatabase().query(getTableName(), null, selection, selectionArgs, null, null, null);
        if (cursor == null) {
            return null;
        }
        try (cursor) {
            if (cursor.moveToFirst()) {
                return convert(cursor);
            } else {
                return null;
            }
        }
    }

    @NonNull
    public List<DistributionListModel> filter(@Nullable DistributionListService.DistributionListFilter filter) {
        //sort by id!
        @Nullable String orderBy = null;
        // do not show hidden distribution lists by default
        @Nullable String where = DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST + " !=1";

        if (filter != null) {
            if (!filter.sortingByDate()) {
                orderBy = DistributionListModel.COLUMN_CREATED_AT + " " + (filter.sortingAscending() ? "ASC" : "DESC");
            }
            if (filter.showHidden()) {
                where = null;
            }
        }

        final @NonNull QueryBuilder queryBuilder = new QueryBuilder();
        if (where != null) {
            queryBuilder.appendWhere(where);
        }
        return convert(queryBuilder, orderBy);
    }

    private long getUniqueId() {
        int attemptsLeft = 100;
        long distributionListId;
        do {
            distributionListId = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        } while (doesIdExist(distributionListId) && attemptsLeft-- > 0);
        return distributionListId;
    }

    private boolean doesIdExist(long id) {
        final @Nullable Cursor cursor = getReadableDatabase().query(
            getTableName(),
            null,
            DistributionListModel.COLUMN_ID + "=?",
            new String[]{String.valueOf(id)},
            null,
            null,
            null,
            null
        );
        if (cursor == null) {
            return false;
        }
        try (cursor) {
            return cursor.getCount() > 0;
        }
    }

    public static class Creator implements DatabaseCreationProvider {
        @Override
        @NonNull
        public String[] getCreationStatements() {
            return new String[]{
                "CREATE TABLE `" + DistributionListModel.TABLE + "` (" +
                    "`" + DistributionListModel.COLUMN_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`" + DistributionListModel.COLUMN_NAME + "` VARCHAR, " +
                    "`" + DistributionListModel.COLUMN_CREATED_AT + "` BIGINT, " +
                    "`" + DistributionListModel.COLUMN_LAST_UPDATE + "` INTEGER, " +
                    "`" + DistributionListModel.COLUMN_CONVERSATION_VISIBILITY + "` INTEGER DEFAULT 0 NOT NULL, " +
                    "`" + DistributionListModel.COLUMN_CONVERSATION_CATEGORY + "` INTEGER DEFAULT 0 NOT NULL," +
                    "`" + DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST + "` TINYINT DEFAULT 0 " +
                    ");"
            };
        }
    }
}
