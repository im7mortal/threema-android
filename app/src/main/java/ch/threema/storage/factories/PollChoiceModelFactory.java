package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.poll.PollChoiceModel;

import static ch.threema.common.JavaCompat.isNullOrEmpty;

public class PollChoiceModelFactory extends ModelFactory {
    public PollChoiceModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, PollChoiceModel.TABLE);
    }

    public List<PollChoiceModel> getAll() {
        return convertList(getReadableDatabase().query(this.getTableName(),
            null,
            null,
            null,
            null,
            null,
            null));
    }

    public List<PollChoiceModel> getByPollId(int pollId) {
        return convertList(getReadableDatabase().query(this.getTableName(),
            null,
            PollChoiceModel.COLUMN_POLL_ID + "=?",
            new String[]{
                String.valueOf(pollId)
            },
            null,
            null,
            "`" + PollChoiceModel.COLUMN_ORDER + "` ASC"));
    }

    public PollChoiceModel getByPollIdAndApiChoiceId(int pollId, int apiChoiceId) {
        return getFirst(
            PollChoiceModel.COLUMN_POLL_ID + "=? "
                + "AND " + PollChoiceModel.COLUMN_API_CHOICE_ID + "=?",
            new String[]{
                String.valueOf(pollId),
                String.valueOf(apiChoiceId)
            });
    }


    public PollChoiceModel getById(int id) {
        return getFirst(
            PollChoiceModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(id)
            });
    }

    public List<PollChoiceModel> convert(QueryBuilder queryBuilder,
                                         String[] args,
                                         String orderBy) {
        queryBuilder.setTables(this.getTableName());
        return convertList(queryBuilder.query(
            getReadableDatabase(),
            null,
            null,
            args,
            null,
            null,
            orderBy));
    }

    private List<PollChoiceModel> convertList(Cursor c) {

        List<PollChoiceModel> result = new ArrayList<>();
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    result.add(convert(c));
                }
            } finally {
                c.close();
            }
        }
        return result;
    }

    private PollChoiceModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final PollChoiceModel c = new PollChoiceModel();

            //convert default
            new CursorHelper(cursor, getColumnIndexCache()).current(new CursorHelper.Callback() {
                @Override

                public boolean next(CursorHelper cursorFactory) {
                    c
                        .setId(cursorFactory.getInt(PollChoiceModel.COLUMN_ID))
                        .setPollId(cursorFactory.getInt(PollChoiceModel.COLUMN_POLL_ID))
                        .setApiPollChoiceId(cursorFactory.getInt(PollChoiceModel.COLUMN_API_CHOICE_ID))
                        .setName(cursorFactory.getString(PollChoiceModel.COLUMN_NAME))
                        .setVoteCount(cursorFactory.getInt(PollChoiceModel.COLUMN_VOTE_COUNT))
                        .setOrder(cursorFactory.getInt(PollChoiceModel.COLUMN_ORDER))
                        .setCreatedAt(cursorFactory.getInstant(PollChoiceModel.COLUMN_CREATED_AT))
                        .setModifiedAt(cursorFactory.getInstant(PollChoiceModel.COLUMN_MODIFIED_AT));

                    String type = cursorFactory.getString(PollChoiceModel.COLUMN_TYPE);
                    if (!isNullOrEmpty(type)) {
                        c.setType(PollChoiceModel.Type.valueOf(type));
                    }
                    return false;
                }
            });

            return c;
        }

        return null;
    }

    public boolean createOrUpdate(PollChoiceModel pollChoiceModel) {

        boolean insert = true;
        if (pollChoiceModel.getId() > 0) {
            Cursor cursor = getReadableDatabase().query(
                this.getTableName(),
                null,
                PollChoiceModel.COLUMN_ID + "=?",
                new String[]{
                    String.valueOf(pollChoiceModel.getId())
                },
                null,
                null,
                null
            );

            if (cursor != null) {
                try {
                    insert = !cursor.moveToNext();
                } finally {
                    cursor.close();
                }
            }
        }


        if (insert) {
            return create(pollChoiceModel);
        } else {
            return update(pollChoiceModel);
        }
    }

    private ContentValues buildContentValues(PollChoiceModel pollChoiceModel) {
        ContentValues contentValues = new ContentValues();

        contentValues.put(PollChoiceModel.COLUMN_POLL_ID, pollChoiceModel.getPollId());
        contentValues.put(PollChoiceModel.COLUMN_API_CHOICE_ID, pollChoiceModel.getApiPollChoiceId());
        contentValues.put(PollChoiceModel.COLUMN_TYPE, pollChoiceModel.getType() != null ? pollChoiceModel.getType().toString() : null);
        contentValues.put(PollChoiceModel.COLUMN_NAME, pollChoiceModel.getName());
        contentValues.put(PollChoiceModel.COLUMN_VOTE_COUNT, pollChoiceModel.getVoteCount());
        contentValues.put("`" + PollChoiceModel.COLUMN_ORDER + "`", pollChoiceModel.getOrder());
        contentValues.put(PollChoiceModel.COLUMN_CREATED_AT, pollChoiceModel.getCreatedAt() != null ? pollChoiceModel.getCreatedAt().toEpochMilli() : null);
        contentValues.put(PollChoiceModel.COLUMN_MODIFIED_AT, pollChoiceModel.getModifiedAt() != null ? pollChoiceModel.getModifiedAt().toEpochMilli() : null);

        return contentValues;
    }

    public boolean create(PollChoiceModel pollChoiceModel) {
        ContentValues contentValues = buildContentValues(pollChoiceModel);
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        if (newId > 0) {
            pollChoiceModel.setId((int) newId);
            return true;
        }
        return false;
    }

    private boolean update(PollChoiceModel pollChoiceModel) {
        ContentValues contentValues = buildContentValues(pollChoiceModel);
        getWritableDatabase().update(this.getTableName(),
            contentValues,
            PollChoiceModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(pollChoiceModel.getId())
            });
        return true;
    }


    public int delete(PollChoiceModel pollChoiceModel) {
        return getWritableDatabase().delete(this.getTableName(),
            PollChoiceModel.COLUMN_ID + "=?",
            new String[]{
                String.valueOf(pollChoiceModel.getId())
            });
    }

    public int deleteByPollId(int pollId) {
        return getWritableDatabase().delete(
            this.getTableName(),
            PollChoiceModel.COLUMN_POLL_ID + "=?",
            new String[]{
                String.valueOf(pollId)
            }
        );
    }

    private PollChoiceModel getFirst(String selection, String[] selectionArgs) {
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
            try {
                if (cursor.moveToFirst()) {
                    return convert(cursor);
                }
            } finally {
                cursor.close();
            }
        }

        return null;
    }
    
    public static class Creator implements DatabaseCreationProvider {
        @Override
        @NonNull
        public String [] getCreationStatements() {
            return new String[]{
                "CREATE TABLE `ballot_choice` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `ballotId` INTEGER , `apiBallotChoiceId` INTEGER , `type` VARCHAR , `name` VARCHAR , `voteCount` INTEGER , `order` INTEGER NOT NULL , `createdAt` BIGINT , `modifiedAt` BIGINT )",

                // indices
                "CREATE UNIQUE INDEX `apiBallotChoiceId` ON `ballot_choice` ( `ballotId`, `apiBallotChoiceId` )"
            };
        }
    }
}
