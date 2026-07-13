package ch.threema.storage.models;

import java.time.Instant;

/**
 * Dummy model for date separator in ComposeMessageAdapter
 */
public class DateSeparatorMessageModel extends AbstractMessageModel {
    @Override
    public int getId() {
        return 0;
    }

    @Override
    public String getUid() {
        return null;
    }

    @Override
    public boolean isStatusMessage() {
        return true;
    }

    @Override
    public String getIdentity() {
        return null;
    }

    @Override
    public boolean isOutbox() {
        return false;
    }

    @Override
    public MessageType getType() {
        return MessageType.DATE_SEPARATOR;
    }

    @Override
    public String getBody() {
        return null;
    }


    @Override
    public boolean isRead() {
        return false;
    }

    @Override
    public boolean isSaved() {
        return false;
    }

    @Override
    public MessageState getState() {
        return null;
    }

    @Override
    public Instant getModifiedAt() {
        return null;
    }

    @Override
    public Instant getPostedAt() {
        return null;
    }

    @Override
    public String getApiMessageId() {
        return null;
    }
}
