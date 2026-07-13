package ch.threema.storage.models;

import java.time.Instant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.data.datatypes.ConversationId;

public class ConversationTagModel {
    public static final String TABLE = "conversation_tag";
    public static final String COLUMN_CONVERSATION_UID = "conversationUid";
    public static final String COLUMN_TAG = "tag";
    public static final String COLUMN_CREATED_AT = "createdAt";

    private final @NonNull ConversationId conversationId;
    private final @Nullable String tag;
    private final @Nullable Instant createdAt;

    public ConversationTagModel(@NonNull ConversationId conversationId, @Nullable String tag, @Nullable Instant createdAt) {
        this.conversationId = conversationId;
        this.tag = tag;
        this.createdAt = (createdAt != null) ? createdAt : Instant.now();
    }

    public ConversationTagModel(@NonNull ConversationId conversationId, @NonNull ConversationTag tag) {
        this(conversationId, tag.value, null);
    }

    @NonNull
    public ConversationId getConversationId() {
        return this.conversationId;
    }

    @Nullable
    public String getTag() {
        return this.tag;
    }

    @Nullable
    public Instant getCreatedAt() {
        return this.createdAt;
    }
}
