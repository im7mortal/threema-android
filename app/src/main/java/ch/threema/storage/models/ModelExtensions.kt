package ch.threema.storage.models

import ch.threema.common.minus
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.storage.models.data.MessageContentsType
import ch.threema.storage.models.data.media.FileDataModel
import ch.threema.storage.models.group.GroupModelOld
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun MessageModel.getFileName(): String? =
    takeIf { it.type == MessageType.FILE }?.fileData?.fileName

fun MessageModel.getConversationId(): ConversationId =
    when (this) {
        is GroupMessageModel -> {
            GroupConversationId(
                groupDatabaseId = groupId.toLong(),
            )
        }
        is DistributionListMessageModel -> {
            DistributionListConversationId(
                distributionListId = distributionListId,
            )
        }
        is ContactMessageModel -> {
            val identity = identity
            requireNotNull(identity) { "Message is missing identity" }
            ContactConversationId(identity)
        }
    }

fun MessageModel.belongsToSameConversationAs(other: MessageModel) =
    getConversationId() == other.getConversationId()

fun MessageModel.supportsEmojiReactions() =
    !isDeleted &&
        !isStatusMessage &&
        type?.supportsEmojiReactions == true &&
        (this is ContactMessageModel || this is GroupMessageModel)

fun MessageModel.isQuote(): Boolean =
    quotedMessageIdString != null

fun MessageModel.isQuotable(): Boolean {
    if (isDeleted) {
        return false
    }
    return when (type) {
        MessageType.TEXT,
        MessageType.FILE,
        MessageType.POLL,
        MessageType.LOCATION,
        -> apiMessageId != null
        else -> false
    }
}

/**
 * Check whether the user should be able to edit the given message.
 *
 * @param belongsToNotesGroup Whether this message is part of a Notes Group, i.e., a group with only the user in it
 * @param editTime The time at which the message would be edited, usually "now"
 * @param getMessageTime Used to determine the relevant date of the message, for determining its age
 * @return `true` if this message can still be edited, `false` otherwise
 */
@JvmOverloads
fun MessageModel.canBeEdited(
    belongsToNotesGroup: Boolean = false,
    editTime: Instant = Instant.now(),
    getMessageTime: MessageModel.() -> Instant? = MessageModel::createdAt,
): Boolean =
    type?.canBeEdited == true &&
        !isStatusMessage &&
        isOutbox &&
        (belongsToNotesGroup || getMessageTime()?.let { messageTime -> editTime - messageTime <= EditMessage.EDIT_MESSAGES_MAX_AGE } == true) &&
        (this is ContactMessageModel || this is GroupMessageModel) &&
        (postedAt != null || state == MessageState.SENDFAILED) &&
        !isDeleted

fun MessageModel.canBeStarred(): Boolean =
    (this is ContactMessageModel || this is GroupMessageModel) &&
        (
            type == MessageType.TEXT ||
                type == MessageType.FILE ||
                type == MessageType.LOCATION ||
                type == MessageType.POLL
            )

fun MessageModel.canSendImageReply(): Boolean =
    messageContentsType == MessageContentsType.IMAGE && !isDeleted && fileData.isDownloaded

fun List<MessageModel>.findIndexByMessageId(messageId: Int): Int? =
    indexOfFirst { message -> message.id == messageId }
        .takeUnless { it == -1 }

val GroupModelOld.conversationId: GroupConversationId
    get() = GroupConversationId(id.toLong())

fun FileDataModel.getDuration(): Duration =
    durationSeconds.seconds
