package ch.threema.app.services

import ch.threema.base.SessionScoped
import ch.threema.data.datatypes.ConversationId
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.ConversationTag
import ch.threema.storage.models.ConversationTagModel

@SessionScoped
interface ConversationTagService {

    /**
     * Remove the given tag of the conversation with the provided [ConversationId].
     *
     * @return True if the tag was removed and false if it never existed before.
     */
    fun removeTag(conversationId: ConversationId, tag: ConversationTag, triggerSource: TriggerSource): Boolean

    /**
     * Tag the conversation with the given [ConversationTag].
     *
     * @return True if the tag was newly created and false if the tag was already present.
     */
    fun addTag(conversationId: ConversationId, tag: ConversationTag, triggerSource: TriggerSource): Boolean

    /**
     * Toggle the [ConversationTag] for the conversation
     *
     * @return `true` if the conversation is tagged after the toggle operation, `false` otherwise.
     */
    fun toggle(conversationId: ConversationId, tag: ConversationTag, triggerSource: TriggerSource): Boolean

    /**
     * Return true, if conversation is tagged with [ConversationTag]
     */
    fun isTaggedWith(conversationId: ConversationId, tag: ConversationTag): Boolean

    /**
     * Remove all tags linked with the given conversation id
     */
    fun removeAll(conversationId: ConversationId, triggerSource: TriggerSource)

    /**
     * Get all tags regardless of type
     */
    fun getAll(): List<ConversationTagModel>

    /**
     * Get all conversation ids that are tagged with the provided type.
     */
    fun getConversationIdsByTag(tag: ConversationTag): List<ConversationId>

    /**
     * Return the number of conversations with the provided tag
     *
     * @return number of conversations or 0 if there is none
     */
    fun getCount(tag: ConversationTag): Long
}
