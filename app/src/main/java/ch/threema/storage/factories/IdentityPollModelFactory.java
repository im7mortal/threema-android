package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;

import androidx.annotation.NonNull;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.models.poll.IdentityPollModel;

public class IdentityPollModelFactory extends ModelFactory {

    public IdentityPollModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, IdentityPollModel.TABLE);
    }

    public IdentityPollModel getByIdentityAndPollId(String identity, int pollId) {
        return getFirst(
            IdentityPollModel.COLUMN_IDENTITY + "=? "
                + "AND " + IdentityPollModel.COLUMN_POLL_ID + "=?",
            new String[]{
                identity,
                String.valueOf(pollId)
            });
    }

    public IdentityPollModel getByPollId(int pollModelId) {
        return getFirst(
            IdentityPollModel.COLUMN_POLL_ID + "=?",
            new String[]{
                String.valueOf(pollModelId)
            });
    }

    private IdentityPollModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final IdentityPollModel c = new IdentityPollModel();

            //convert default
            new CursorHelper(cursor, getColumnIndexCache()).current(new CursorHelper.Callback() {
                @Override
                public boolean next(CursorHelper cursorHelper) {
                    c
                        .setId(cursorHelper.getInt(IdentityPollModel.COLUMN_ID))
                        .setPollId(cursorHelper.getInt(IdentityPollModel.COLUMN_POLL_ID))
                        .setIdentity(cursorHelper.getString(IdentityPollModel.COLUMN_IDENTITY));
                    return false;
                }
            });

            return c;
        }

        return null;
    }

    private ContentValues buildContentValues(IdentityPollModel identityPollModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(IdentityPollModel.COLUMN_IDENTITY, identityPollModel.getIdentity());
        contentValues.put(IdentityPollModel.COLUMN_POLL_ID, identityPollModel.getPollId());

        return contentValues;
    }

    public boolean create(IdentityPollModel identityPollModel) {
        ContentValues contentValues = buildContentValues(identityPollModel);
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        if (newId > 0) {
            identityPollModel.setId((int) newId);
            return true;
        }
        return false;
    }

    public int deleteByPollId(int pollId) {
        return getWritableDatabase().delete(this.getTableName(),
            IdentityPollModel.COLUMN_POLL_ID + "=?",
            new String[]{
                String.valueOf(pollId)
            });
    }

    private IdentityPollModel getFirst(String selection, String[] selectionArgs) {
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
                "CREATE TABLE `identity_ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `identity` VARCHAR NOT NULL , `ballotId` INTEGER NOT NULL )",
                "CREATE UNIQUE INDEX `identityBallotId` ON `identity_ballot` ( `identity`, `ballotId` )"
            };
        }
    }
}
