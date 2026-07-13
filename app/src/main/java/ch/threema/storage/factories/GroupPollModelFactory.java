package ch.threema.storage.factories;

import android.content.ContentValues;

import android.database.Cursor;

import androidx.annotation.NonNull;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseCreationProvider;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.models.poll.GroupPollModel;

public class GroupPollModelFactory extends ModelFactory {

    public GroupPollModelFactory(DatabaseProvider databaseProvider) {
        super(databaseProvider, GroupPollModel.TABLE);
    }

    public GroupPollModel getByGroupIdAndPollId(int groupId, int pollId) {
        return getFirst(
            GroupPollModel.COLUMN_GROUP_ID + "=? "
                + "AND " + GroupPollModel.COLUMN_POLL_ID + "=?",
            new String[]{
                String.valueOf(groupId),
                String.valueOf(pollId)
            });
    }


    public GroupPollModel getByPollId(int pollModelId) {
        return getFirst(
            GroupPollModel.COLUMN_POLL_ID + "=?",
            new String[]{
                String.valueOf(pollModelId)
            });
    }

    private GroupPollModel convert(Cursor cursor) {
        if (cursor != null && cursor.getPosition() >= 0) {
            final GroupPollModel c = new GroupPollModel();

            //convert default
            new CursorHelper(cursor, getColumnIndexCache()).current(new CursorHelper.Callback() {
                @Override
                public boolean next(CursorHelper cursorHelper) {
                    c
                        .setId(cursorHelper.getInt(GroupPollModel.COLUMN_ID))
                        .setPollId(cursorHelper.getInt(GroupPollModel.COLUMN_POLL_ID))
                        .setGroupId(cursorHelper.getInt(GroupPollModel.COLUMN_GROUP_ID));
                    return false;
                }
            });

            return c;
        }

        return null;
    }

    private ContentValues buildContentValues(GroupPollModel groupPollModel) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(GroupPollModel.COLUMN_GROUP_ID, groupPollModel.getGroupId());
        contentValues.put(GroupPollModel.COLUMN_POLL_ID, groupPollModel.getPollId());

        return contentValues;
    }

    public boolean create(GroupPollModel groupPollModel) {
        ContentValues contentValues = buildContentValues(groupPollModel);
        long newId = getWritableDatabase().insertOrThrow(this.getTableName(), null, contentValues);
        if (newId > 0) {
            groupPollModel.setId((int) newId);
            return true;
        }
        return false;
    }

    public int deleteByPollId(int pollId) {
        return getWritableDatabase().delete(this.getTableName(),
            GroupPollModel.COLUMN_POLL_ID + "=?",
            new String[]{
                String.valueOf(pollId)
            });
    }

    private GroupPollModel getFirst(String selection, String[] selectionArgs) {
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
                "CREATE TABLE `group_ballot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT , `groupId` INTEGER NOT NULL , `ballotId` INTEGER NOT NULL )",
                "CREATE UNIQUE INDEX `groupBallotId` ON `group_ballot` ( `groupId`, `ballotId` )"
            };
        }
    }
}
