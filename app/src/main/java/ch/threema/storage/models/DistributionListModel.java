package ch.threema.storage.models;

import static ch.threema.common.StringExtensionsKt.truncateUTF8String;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.Objects;

import ch.threema.data.datatypes.ConversationVisibility;
import ch.threema.data.datatypes.IdColor;

public class DistributionListModel implements ReceiverModel {
    public static final int DISTRIBUTION_LIST_NAME_MAX_LENGTH_BYTES = 256;

    public static final String TABLE = "distribution_list";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_CREATED_AT = "createdAt";
    public static final String COLUMN_LAST_UPDATE = "lastUpdateAt"; /* date when the conversation was last updated */
    public static final String COLUMN_CONVERSATION_VISIBILITY = "conversationVisibility";
    public static final String COLUMN_IS_ADHOC_DISTRIBUTION_LIST = "isHidden"; /* whether this is an ad-hoc distribution list */

    private long id;
    private String name;
    private Instant createdAt;
    @Nullable
    private Instant lastUpdate;
    @NonNull
    private ConversationVisibility conversationVisibility = ConversationVisibility.NORMAL;
    private boolean isAdHocDistributionList;
    private IdColor idColor = IdColor.invalid();

    // dummy class
    @Nullable
    public String getName() {
        return this.name;
    }

    public DistributionListModel setName(@Nullable String name) {
        this.name = name != null
            ? truncateUTF8String(name, DISTRIBUTION_LIST_NAME_MAX_LENGTH_BYTES)
            : null;
        return this;
    }

    public long getId() {
        return this.id;
    }

    public DistributionListModel setId(long id) {
        this.id = id;
        // Invalidate id color as it might have changed
        this.idColor = IdColor.invalid();
        return this;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public DistributionListModel setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    @Override
    public DistributionListModel setLastUpdate(@Nullable Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
        return this;
    }

    @Override
    public @Nullable Instant getLastUpdate() {
        // Note: Never return null for distribution lists, they should always be visible
        return this.lastUpdate == null ? Instant.EPOCH : this.lastUpdate;
    }

    @Override
    public boolean isArchived() {
        return conversationVisibility == ConversationVisibility.ARCHIVED;
    }

    @NonNull
    public ConversationVisibility getConversationVisibility() {
        return conversationVisibility;
    }

    @NonNull
    public DistributionListModel setConversationVisibility(@NonNull ConversationVisibility conversationVisibility) {
        this.conversationVisibility = conversationVisibility;
        return this;
    }

    /**
     * Set whether or not this is an ad-hoc distribution list.
     * <p>
     * Setting this to true will result in the distribution list being hidden from the
     * conversation list.
     */
    public DistributionListModel setAdHocDistributionList(boolean isAdHocDistributionList) {
        this.isAdHocDistributionList = isAdHocDistributionList;
        return this;
    }

    /**
     * Return whether or not this is an ad-hoc distribution list.
     */
    public boolean isAdHocDistributionList() {
        return this.isAdHocDistributionList;
    }

    @Override
    public boolean isHidden() {
        // Hide ad-hoc distribution lists from conversation list
        return this.isAdHocDistributionList();
    }

    public IdColor getIdColor() {
        if (!idColor.isValid()) {
            idColor = IdColor.ofDistributionList(id);
        }
        return idColor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DistributionListModel)) return false;
        DistributionListModel that = (DistributionListModel) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
