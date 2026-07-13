package ch.threema.storage.models

import ch.threema.data.datatypes.GroupConversationId
import ch.threema.storage.models.group.GroupModelOld

fun AbstractMessageModel.getFileName(): String? =
    takeIf { it.type == MessageType.FILE }?.fileData?.fileName

val GroupModelOld.conversationId: GroupConversationId
    get() = GroupConversationId(id.toLong())
