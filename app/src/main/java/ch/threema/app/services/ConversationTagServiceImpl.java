package ch.threema.app.services;

import androidx.annotation.NonNull;

import java.util.List;

import ch.threema.data.datatypes.ConversationId;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.factories.ConversationTagFactory;
import ch.threema.storage.models.ConversationTagModel;
import ch.threema.storage.models.ConversationTag;

public class ConversationTagServiceImpl implements ConversationTagService {
    @NonNull
    private final ConversationTagFactory conversationTagFactory;

    public ConversationTagServiceImpl(@NonNull ConversationTagFactory conversationTagFactory) {
        this.conversationTagFactory = conversationTagFactory;
    }

    @Override
    public boolean removeTag(@NonNull ConversationId conversationId, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (!isTaggedWith(conversationId, tag)) {
            return false;
        }
        conversationTagFactory.deleteByConversationIdAndTag(conversationId, tag);
        return true;
    }

    @Override
    public boolean addTag(@NonNull ConversationId conversationId, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (isTaggedWith(conversationId, tag)) {
            return false;
        }
        conversationTagFactory.create(
            new ConversationTagModel(conversationId, tag)
        );
        return true;
    }

    @Override
    public boolean toggle(@NonNull ConversationId conversationId, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource) {
        if (this.isTaggedWith(conversationId, tag)) {
            // remove
            conversationTagFactory.deleteByConversationIdAndTag(conversationId, tag);
            return false;
        } else {
            // Add
            conversationTagFactory.create(new ConversationTagModel(conversationId, tag));
            return true;
        }
    }

    @Override
    public boolean isTaggedWith(@NonNull ConversationId conversationId, @NonNull ConversationTag tag) {
        return conversationTagFactory.getByConversationIdAndTag(conversationId, tag) != null;
    }

    @Override
    public void removeAll(@NonNull ConversationId conversationId, @NonNull TriggerSource triggerSource) {
        conversationTagFactory.deleteByConversationId(conversationId);
    }

    @NonNull
    @Override
    public List<ConversationTagModel> getAll() {
        return conversationTagFactory.getAll();
    }

    @Override
    @NonNull
    public List<ConversationId> getConversationIdsByTag(@NonNull ConversationTag tag) {
        return conversationTagFactory.getAllConversationIdsByTag(tag);
    }

    @Override
    public long getCount(@NonNull ConversationTag tag) {
        return conversationTagFactory.countByTag(tag);
    }
}
