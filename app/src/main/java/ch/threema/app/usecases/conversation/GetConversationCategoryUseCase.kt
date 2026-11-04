package ch.threema.app.usecases.conversation

import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationId

interface GetConversationCategoryUseCase {
    fun call(conversationId: ConversationId): ConversationCategory
}
