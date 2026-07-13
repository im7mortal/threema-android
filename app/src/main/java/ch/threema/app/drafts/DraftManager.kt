package ch.threema.app.drafts

import androidx.annotation.AnyThread
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.ConversationIdObfuscated
import ch.threema.domain.models.MessageId
import kotlinx.coroutines.flow.StateFlow

@AnyThread
interface DraftManager {

    val drafts: StateFlow<Map<ConversationIdObfuscated, MessageDraft>>

    /**
     * Returns the draft for a conversation, or null if there is no draft.
     * If there is a draft, its text is guaranteed to be non-blank.
     */
    fun get(conversationId: ConversationId): MessageDraft?

    fun set(conversationId: ConversationId, text: String?) {
        set(conversationId, text, quotedMessageId = null)
    }

    /**
     * Stores a draft for a conversation. If [text] is null or blank, the draft will be removed instead.
     */
    fun set(conversationId: ConversationId, text: String?, quotedMessageId: MessageId?)

    fun remove(conversationId: ConversationId)
}
