package ch.threema.app.libthreema.conversion

import ch.threema.data.datatypes.ConversationCategory
import ch.threema.libthreema.ConversationCategory as LibthreemaConversationCategory

fun ConversationCategory.toLibthreemaConversationCategory(): LibthreemaConversationCategory = when (this) {
    ConversationCategory.DEFAULT -> LibthreemaConversationCategory.DEFAULT
    ConversationCategory.PROTECTED -> LibthreemaConversationCategory.PROTECTED
}

fun LibthreemaConversationCategory.toConversationCategory(): ConversationCategory = when (this) {
    LibthreemaConversationCategory.DEFAULT -> ConversationCategory.DEFAULT
    LibthreemaConversationCategory.PROTECTED -> ConversationCategory.PROTECTED
}
