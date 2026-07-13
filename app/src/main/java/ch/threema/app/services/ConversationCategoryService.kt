package ch.threema.app.services

import ch.threema.base.SessionScoped
import ch.threema.data.datatypes.ConversationId

/**
 *  This service is used to keep track of private conversations.
 *
 *  TODO(ANDR-3010): Move the conversation category into the database.
 *
 *  @see ch.threema.protobuf.d2d.sync.ConversationCategory
 */
@SessionScoped
interface ConversationCategoryService {

    /**
     *  Mark the given conversation as private and reflect the change if MD is active.
     *
     *  @return `true` if the conversation has been marked as private effectively
     */
    fun setPrivateMark(conversationId: ConversationId): Boolean

    /**
     *  Remove the private mark for the given conversation and reflect the change if MD is active.
     *
     *  @return `true` if private mark has been removed effectively for the given conversation
     */
    fun removePrivateMark(conversationId: ConversationId): Boolean

    fun isMarkedAsPrivate(conversationId: ConversationId): Boolean

    /**
     *  Mark the given conversation as private *without* reflecting the change.
     */
    fun persistAddPrivateMark(conversationId: ConversationId)

    /**
     *  Remove the private mark from the given conversation *without* reflecting the change.
     */
    fun persistRemovePrivateMark(conversationId: ConversationId)

    /**
     *  @return `true` if at least one conversation is marked as private
     */
    fun hasAnyPrivateMarks(): Boolean

    /**
     *  Invalidate the cache. This is only required if the preferences are modified without using this service.
     */
    fun invalidateCache()
}
