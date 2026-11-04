package ch.threema.storage.factories;

import static ch.threema.android.CursorExtensionsKt.readCountQueryResult;
import static ch.threema.common.DatabaseHelpers.makePlaceholders;
import static ch.threema.common.JavaCompat.isNullOrEmpty;
import static ch.threema.storage.models.data.DisplayTag.DISPLAY_TAG_STARRED;

import android.content.ContentValues;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import net.zetetic.database.sqlcipher.SQLiteStatement;

import org.slf4j.Logger;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;

import ch.threema.app.services.MessageService;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityMode;
import ch.threema.storage.CursorHelper;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.DisplayTag;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

abstract class MessageModelFactory extends ModelFactory {
    private static final Logger logger = getThreemaLogger("MessageModelFactory");

    MessageModelFactory(DatabaseProvider databaseProvider, String tableName) {
        super(databaseProvider, tableName);
    }

    void convert(final MessageModel messageModel, CursorHelper cursorFactory) {
        cursorFactory.current(new CursorHelper.Callback() {
            @Override
            public boolean next(CursorHelper cursorFactory) {
                ForwardSecurityMode forwardSecurityMode = ForwardSecurityMode.NONE;
                Integer fsmValue = cursorFactory.getInt(MessageModel.COLUMN_FORWARD_SECURITY_MODE);
                if (fsmValue != null) {
                    forwardSecurityMode = ForwardSecurityMode.getByValue(fsmValue);
                }

                messageModel.setId(cursorFactory.getInt(MessageModel.COLUMN_ID));
                messageModel.setUid(cursorFactory.getString(MessageModel.COLUMN_UID));
                messageModel.setApiMessageId(cursorFactory.getString(MessageModel.COLUMN_API_MESSAGE_ID));
                messageModel.setIdentity(cursorFactory.getString(MessageModel.COLUMN_IDENTITY));
                messageModel.setOutbox(cursorFactory.getBoolean(MessageModel.COLUMN_OUTBOX));
                messageModel.setCorrelationId(cursorFactory.getString(MessageModel.COLUMN_CORRELATION_ID));
                messageModel.setBody(cursorFactory.getString(MessageModel.COLUMN_BODY));
                messageModel.setRead(cursorFactory.getBoolean(MessageModel.COLUMN_IS_READ));
                messageModel.setSaved(cursorFactory.getBoolean(MessageModel.COLUMN_IS_SAVED));
                messageModel.setPostedAt(cursorFactory.getInstant(MessageModel.COLUMN_POSTED_AT));
                messageModel.setCreatedAt(cursorFactory.getInstant(MessageModel.COLUMN_CREATED_AT));
                messageModel.setModifiedAt(cursorFactory.getInstant(MessageModel.COLUMN_MODIFIED_AT));
                messageModel.setCaption(cursorFactory.getString(MessageModel.COLUMN_CAPTION));
                messageModel.setQuotedMessageIdString(cursorFactory.getString(MessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID));
                messageModel.setMessageContentsType(getIntOrZero(cursorFactory.getInt(MessageModel.COLUMN_MESSAGE_CONTENTS_TYPE)));
                messageModel.setMessageFlags(getIntOrZero(cursorFactory.getInt(MessageModel.COLUMN_MESSAGE_FLAGS)));
                messageModel.setDeliveredAt(cursorFactory.getInstant(MessageModel.COLUMN_DELIVERED_AT));
                messageModel.setReadAt(cursorFactory.getInstant(MessageModel.COLUMN_READ_AT));
                messageModel.setEditedAt(cursorFactory.getInstant(MessageModel.COLUMN_EDITED_AT));
                messageModel.setDeletedAt(cursorFactory.getInstant(MessageModel.COLUMN_DELETED_AT));
                messageModel.setForwardSecurityMode(forwardSecurityMode);
                messageModel.setDisplayTags(getIntOrZero(cursorFactory.getInt(MessageModel.COLUMN_DISPLAY_TAGS)));

                String stateString = cursorFactory.getString(MessageModel.COLUMN_STATE);
                if (!isNullOrEmpty(stateString)) {
                    try {
                        messageModel.setState(MessageState.valueOf(stateString));
                    } catch (IllegalArgumentException e) {
                        logger.error("Invalid message state {} - ignore", stateString, e);
                    }
                }

                @Nullable Integer type = cursorFactory.getInt(MessageModel.COLUMN_TYPE);
                messageModel.setType(
                    type != null
                        ? MessageType.deserialize(type)
                        : null
                );
                return false;
            }
        });
    }

    // TODO(ANDR-5222): According to crash-reports, there are users who have messages in their DB with unexpected
    //  null values. These messages should be cleanup up, such that this fallback here is no longer necessary.
    private int getIntOrZero(@Nullable Integer integer) {
        return integer != null ? integer : 0;
    }

    ContentValues buildContentValues(MessageModel messageModel) {
        ensureMessageIdIsSetIfRequiredByType(messageModel);
        ensureMessageMayBePersisted(messageModel);
        ContentValues contentValues = new ContentValues();
        contentValues.put(MessageModel.COLUMN_UID, messageModel.getUid());
        contentValues.put(MessageModel.COLUMN_API_MESSAGE_ID, messageModel.getApiMessageId());
        contentValues.put(MessageModel.COLUMN_IDENTITY, messageModel.getIdentity());
        contentValues.put(MessageModel.COLUMN_OUTBOX, messageModel.isOutbox());
        contentValues.put(MessageModel.COLUMN_TYPE, messageModel.getType() != null ? messageModel.getType().serializedValue : null);
        contentValues.put(MessageModel.COLUMN_CORRELATION_ID, messageModel.getCorrelationId());
        contentValues.put(MessageModel.COLUMN_BODY, messageModel.getBody());
        contentValues.put(MessageModel.COLUMN_IS_READ, messageModel.isRead());
        contentValues.put(MessageModel.COLUMN_IS_SAVED, messageModel.isSaved());
        contentValues.put(MessageModel.COLUMN_STATE, messageModel.getState() != null ? messageModel.getState().toString() : null);
        contentValues.put(MessageModel.COLUMN_POSTED_AT, serializeInstant(messageModel.getPostedAt()));
        contentValues.put(MessageModel.COLUMN_CREATED_AT, serializeInstant(messageModel.getCreatedAt()));
        contentValues.put(MessageModel.COLUMN_MODIFIED_AT, serializeInstant(messageModel.getModifiedAt()));
        contentValues.put(MessageModel.COLUMN_IS_STATUS_MESSAGE, messageModel.isStatusMessage());
        contentValues.put(MessageModel.COLUMN_CAPTION, messageModel.getCaption());
        contentValues.put(MessageModel.COLUMN_QUOTED_MESSAGE_API_MESSAGE_ID, messageModel.getQuotedMessageIdString());
        contentValues.put(MessageModel.COLUMN_MESSAGE_CONTENTS_TYPE, messageModel.getMessageContentsType());
        contentValues.put(MessageModel.COLUMN_MESSAGE_FLAGS, messageModel.getMessageFlags());
        contentValues.put(MessageModel.COLUMN_DELIVERED_AT, serializeInstant(messageModel.getDeliveredAt()));
        contentValues.put(MessageModel.COLUMN_READ_AT, serializeInstant(messageModel.getReadAt()));
        contentValues.put(MessageModel.COLUMN_EDITED_AT, serializeInstant(messageModel.getEditedAt()));
        contentValues.put(MessageModel.COLUMN_DELETED_AT, serializeInstant(messageModel.getDeletedAt()));
        contentValues.put(MessageModel.COLUMN_FORWARD_SECURITY_MODE, messageModel.getForwardSecurityMode() != null ? messageModel.getForwardSecurityMode().getValue() : null);
        contentValues.put(MessageModel.COLUMN_DISPLAY_TAGS, messageModel.getDisplayTags());

        return contentValues;
    }

    @Nullable
    private static Long serializeInstant(@Nullable Instant instant) {
        return instant != null ? instant.toEpochMilli() : null;
    }

    private void ensureMessageIdIsSetIfRequiredByType(@NonNull MessageModel message) {
        var type = message.getType();
        if (type != null && type.getRequiresMessageId() && message.getApiMessageId() == null) {
            throw new IllegalArgumentException("Message of type " + type + " requires a message id");
        }
    }

    private void ensureMessageMayBePersisted(@NonNull MessageModel message) {
        var type = message.getType();
        if (type == MessageType.VIRTUAL) {
            throw new IllegalArgumentException("Virtual messages can not be persisted");
        }
    }

    void appendFilter(QueryBuilder queryBuilder, @Nullable MessageService.MessageFilter filter, List<String> placeholders) {
        if (filter != null) {
            if (!filter.withStatusMessages()) {
                queryBuilder.appendWhere(MessageModel.COLUMN_IS_STATUS_MESSAGE + "=0");
            }
            if (filter.onlyUnread()) {
                queryBuilder.appendWhere(MessageModel.COLUMN_OUTBOX + "=0");
                queryBuilder.appendWhere(MessageModel.COLUMN_IS_READ + "=0");
                queryBuilder.appendWhere(MessageModel.COLUMN_IS_STATUS_MESSAGE + "=0");
            }

            if (!filter.withUnsaved()) {
                queryBuilder.appendWhere(MessageModel.COLUMN_IS_SAVED + "=1");
            }

            if (filter.types() != null && filter.types().length > 0) {
                queryBuilder.appendWhere(MessageModel.COLUMN_TYPE + " IN (" + makePlaceholders(filter.types().length) + ")");
                for (MessageType f : filter.types()) {
                    placeholders.add(String.valueOf(f.serializedValue));
                }
            }

            if (filter.contentTypes() != null && filter.contentTypes().length > 0) {
                queryBuilder.appendWhere(MessageModel.COLUMN_MESSAGE_CONTENTS_TYPE + " IN (" + makePlaceholders(filter.contentTypes().length) + ")");
                for (@MessageContentsType int f : filter.contentTypes()) {
                    placeholders.add(String.valueOf(f));
                }
            }

            if (filter.getPageReferenceId() != null && filter.getPageReferenceId() > 0) {
                queryBuilder.appendWhere(MessageModel.COLUMN_ID + "<?");
                placeholders.add(String.valueOf(filter.getPageReferenceId()));
            }

            if (filter.displayTags() != null && filter.displayTags().length > 0) {
                for (@DisplayTag int f : filter.displayTags()) {
                    queryBuilder.appendWhere("(" + MessageModel.COLUMN_DISPLAY_TAGS + " & " + f + ") > 0");
                }
            }
        }
    }

    <T> void postFilter(List<T> input, @Nullable MessageService.MessageFilter filter) {
        if (filter != null && filter.onlyDownloaded()) {
            Iterator<T> i = input.iterator();
            while (i.hasNext()) {
                MessageModel m = (MessageModel) i.next();
                boolean remove = false;
                if (m.getType() == MessageType.FILE) {
                    FileDataModel d = m.getFileData();
                    remove = (d == null || !d.isDownloaded());
                }

                if (remove) {
                    i.remove();
                }
            }
        }
    }

    String limitFilter(@Nullable MessageService.MessageFilter filter) {
        if (filter != null && filter.getPageSize() > 0) {
            return "" + filter.getPageSize();
        }
        return null;
    }

    /**
     * Mark file messages that have not been uploaded completely yet as failed so that users can
     * retry sending them. This affects file messages with state pending (upload did not start yet)
     * and uploading (upload may have been started already). File messages with state sending do not
     * need to be set to failed as they have been uploaded completely and a persistent task has been
     * scheduled. Therefore, these files will get sent as soon as there is a chat server connection.
     */
    public void markUnscheduledFileMessagesAsFailed() {
        ContentValues values = new ContentValues();
        values.put(MessageModel.COLUMN_STATE, MessageState.SENDFAILED.toString());

        try {
            int updated = getWritableDatabase().update(
                this.getTableName(),
                values,
                MessageModel.COLUMN_TYPE + " =?"
                    + " AND " + MessageModel.COLUMN_STATE + " IN (?, ?)"
                    + " AND " + MessageModel.COLUMN_OUTBOX + " = 1",
                new String[]{
                    String.valueOf(MessageType.FILE.serializedValue),
                    MessageState.PENDING.toString(),
                    MessageState.UPLOADING.toString(),
                }
            );

            if (updated > 0) {
                logger.info("{} messages in sending status were updated to sendfailed.", updated);
            }
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @WorkerThread
    public int unstarAllMessages() {
        String query =
            "UPDATE " + this.getTableName() +
                " SET " + MessageModel.COLUMN_DISPLAY_TAGS +
                " = (" + MessageModel.COLUMN_DISPLAY_TAGS +
                " & ~" + DISPLAY_TAG_STARRED +
                ") WHERE (" + MessageModel.COLUMN_DISPLAY_TAGS + " & " + DISPLAY_TAG_STARRED + ") > 0";

        SQLiteStatement statement = getWritableDatabase().compileStatement(query);
        return statement.executeUpdateDelete();
    }

    @WorkerThread
    public long countStarredMessages() throws SQLiteException {
        return readCountQueryResult(getReadableDatabase().rawQuery(
            "SELECT COUNT(*) FROM " + this.getTableName()
                + " WHERE (" + MessageModel.COLUMN_DISPLAY_TAGS + " & " + DISPLAY_TAG_STARRED + ") > 0",
            null
        ));
    }
}
