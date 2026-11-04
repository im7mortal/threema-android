package ch.threema.app.libthreema.conversion

import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.libthreema.ConversationVisibility as LibthreemaConversationVisibility

fun ConversationVisibility.toLibthreemaConversationVisibility(): LibthreemaConversationVisibility = when (this) {
    ConversationVisibility.NORMAL -> LibthreemaConversationVisibility.NORMAL
    ConversationVisibility.ARCHIVED -> LibthreemaConversationVisibility.ARCHIVED
    ConversationVisibility.PINNED -> LibthreemaConversationVisibility.PINNED
}

fun LibthreemaConversationVisibility.toConversationVisibility(): ConversationVisibility = when (this) {
    LibthreemaConversationVisibility.NORMAL -> ConversationVisibility.NORMAL
    LibthreemaConversationVisibility.ARCHIVED -> ConversationVisibility.ARCHIVED
    LibthreemaConversationVisibility.PINNED -> ConversationVisibility.PINNED
}
