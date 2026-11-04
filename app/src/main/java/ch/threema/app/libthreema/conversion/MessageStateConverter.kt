package ch.threema.app.libthreema.conversion

import ch.threema.common.TimeProvider
import ch.threema.common.UUIDGenerator
import ch.threema.common.toEpochMilliInstant
import ch.threema.data.datatypes.MessageHistoryEntry
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.ConversationId as LibthreemaConversationId
import ch.threema.libthreema.MessageDirection
import ch.threema.libthreema.MessageOverridesDeliveryReceiptPolicy as LibthreemaMessageOverridesDeliveryReceiptPolicy
import ch.threema.libthreema.MessageStarMarker
import ch.threema.libthreema.MessageState as LibthreemaMessageState
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.data.DisplayTag
import java.time.Instant

class MessageStateConverter(
    private val messageBodyStateConverter: MessageBodyStateConverter,
    private val uuidGenerator: UUIDGenerator,
    private val timeProvider: TimeProvider,
) {

    fun convertContactMessage(
        contactMessageState: LibthreemaMessageState,
    ): MessageStateConversionResult {
        val conversationId = contactMessageState.conversationId
        require(conversationId is LibthreemaConversationId.Contact)
        val messageModel = ContactMessageModel().apply {
            this.identity = conversationId.v1
        }
        val messageBodyAndRevisions = messageBodyStateConverter.convertToMessageBodyAndRevisions(
            messageStateBody = contactMessageState.body,
        )
        messageModel.applyCommon(
            messageState = contactMessageState,
            messageBodyAndRevisions = messageBodyAndRevisions,
        )
        return MessageStateConversionResult(
            messageModel = messageModel,
            messageHistoryEntries = messageBodyAndRevisions.getMessageHistoryEntriesForDatabase(contactMessageState.createdAt),
            emojiReactions = contactMessageState.getEmojiReactions(),
        )
    }

    fun convertGroupMessage(
        groupMessageState: LibthreemaMessageState,
        groupDatabaseId: GroupDatabaseId,
    ): MessageStateConversionResult {
        val conversationId = groupMessageState.conversationId
        require(conversationId is LibthreemaConversationId.Group)
        val groupMessageModel = GroupMessageModel().apply {
            groupId = groupDatabaseId.toInt()
        }
        val messageBodyAndRevisions = messageBodyStateConverter.convertToMessageBodyAndRevisions(
            messageStateBody = groupMessageState.body,
        )
        groupMessageModel.applyCommon(
            messageState = groupMessageState,
            messageBodyAndRevisions = messageBodyAndRevisions,
        )
        return MessageStateConversionResult(
            messageModel = groupMessageModel,
            messageHistoryEntries = messageBodyAndRevisions.getMessageHistoryEntriesForDatabase(groupMessageState.createdAt),
            emojiReactions = groupMessageState.getEmojiReactions(),
        )
    }

    fun convertDistributionListMessage(
        distributionListMessageState: LibthreemaMessageState,
    ): MessageStateConversionResult {
        val conversationId = distributionListMessageState.conversationId
        require(conversationId is LibthreemaConversationId.DistributionList)
        val distributionListMessageModel = DistributionListMessageModel().apply {
            distributionListId = conversationId.v1.toLong()
        }
        val messageBodyAndRevisions = messageBodyStateConverter.convertToMessageBodyAndRevisions(
            messageStateBody = distributionListMessageState.body,
        )
        distributionListMessageModel.applyCommon(
            messageState = distributionListMessageState,
            messageBodyAndRevisions = messageBodyAndRevisions,
        )
        return MessageStateConversionResult(
            messageModel = distributionListMessageModel,
            messageHistoryEntries = messageBodyAndRevisions.getMessageHistoryEntriesForDatabase(distributionListMessageState.createdAt),
            emojiReactions = distributionListMessageState.getEmojiReactions(),
        )
    }

    fun MessageModel.applyCommon(
        messageState: LibthreemaMessageState,
        messageBodyAndRevisions: MessageBodyAndRevisions,
    ) {
        uid = uuidGenerator.generate().toString()
        isSaved = true
        type = messageBodyAndRevisions.messageType
        body = messageBodyAndRevisions.messageEdits.lastOrNull()?.body ?: messageBodyAndRevisions.originalBody
        setIdentity(
            creatorIdentity = messageState.creatorIdentity,
            conversationId = messageState.conversationId,
            messageDirection = messageState.direction,
        )
        messageId = MessageId(messageIdLong = messageState.messageId.toLong())
        createdAt = messageState.createdAt.toEpochMilliInstant()
        setMessageDirection(messageState.direction)
        readAt = messageState.readAt?.toEpochMilliInstant()
        isRead = readAt != null
        setMessageState(messageState)
        setMessageStarMarker(messageState.starMarker)
        editedAt = messageBodyAndRevisions.messageEdits.lastOrNull()?.createdAt?.toEpochMilliInstant()
        deletedAt = messageBodyAndRevisions.deletedAt
    }

    fun LibthreemaMessageState.getEmojiReactions(): List<EmojiReaction> {
        val now = timeProvider.get()
        return reactions
            .map { reaction ->
                EmojiReaction(
                    reactedAt = reaction.createdAt.toEpochMilliInstant(),
                    creator = Identity(reaction.creatorIdentity),
                    emojiSequence = reaction.emoji.codepoint,
                    readAt = now, // Read state is not included in the backup
                )
            }
    }

    private fun MessageModel.setIdentity(
        creatorIdentity: IdentityString,
        conversationId: LibthreemaConversationId,
        messageDirection: MessageDirection,
    ) {
        when (conversationId) {
            is LibthreemaConversationId.Contact -> {
                // For 1:1 messages, the identity is always set to the chat partner's identity (regardless of whether it is in or outbound)
                this.identity = conversationId.v1
            }

            is LibthreemaConversationId.Group -> {
                // Note that we set the identity only for incoming messages in groups. We set it to the creator (sender) of the message.
                when (messageDirection) {
                    is MessageDirection.Incoming -> this.identity = creatorIdentity
                    is MessageDirection.Outgoing -> this.identity = null
                }
            }

            is LibthreemaConversationId.DistributionList -> {
                // There is no point of setting an identity for distribution list messages. They are always outbound and therefore always from the
                // user (represented by setting it to null).
                this.identity = null
            }
        }
    }

    private fun MessageModel.setMessageDirection(messageDirection: MessageDirection) = when (messageDirection) {
        is MessageDirection.Incoming -> {
            when (messageDirection.v1.overrides.deliveryReceiptPolicy) {
                LibthreemaMessageOverridesDeliveryReceiptPolicy.DEFAULT -> Unit
                LibthreemaMessageOverridesDeliveryReceiptPolicy.DONT_SEND -> {
                    addMessageFlag(ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS)
                }
            }
            this.deliveredAt = messageDirection.v1.receivedAt.toEpochMilliInstant()
            this.isOutbox = false
        }

        is MessageDirection.Outgoing -> {
            this.postedAt = messageDirection.v1.sentAt?.toEpochMilliInstant()
            this.deliveredAt = messageDirection.v1.deliveredAt?.toEpochMilliInstant()
            this.isOutbox = true
        }
    }

    private fun MessageModel.setMessageState(messageState: LibthreemaMessageState) {
        this.state = when (val messageDirection = messageState.direction) {
            is MessageDirection.Incoming -> {
                when {
                    messageState.readAt != null -> MessageState.READ
                    else -> MessageState.DELIVERED
                }
            }

            is MessageDirection.Outgoing -> {
                when {
                    messageState.readAt != null -> MessageState.READ
                    messageDirection.v1.deliveredAt != null -> MessageState.DELIVERED
                    messageDirection.v1.sentAt != null -> MessageState.SENT
                    else -> MessageState.SENDFAILED
                }
            }
        }
    }

    private fun MessageModel.setMessageStarMarker(messageStarMarker: MessageStarMarker) {
        this.displayTags = when (messageStarMarker) {
            MessageStarMarker.NONE -> DisplayTag.DISPLAY_TAG_NONE
            MessageStarMarker.STAR -> DisplayTag.DISPLAY_TAG_STARRED
        }
    }

    private fun MessageBodyAndRevisions.getMessageHistoryEntriesForDatabase(originalMessageCreatedAt: ULong): List<MessageHistoryEntry> {
        if (messageEdits.isEmpty()) {
            return emptyList()
        }

        return buildList {
            add(
                MessageHistoryEntry(
                    createdAt = originalMessageCreatedAt,
                    body = originalBody.orEmpty(),
                ),
            )
            addAll(
                messageEdits
                    .dropLast(1)
                    .map { messageEdit ->
                        MessageHistoryEntry(
                            createdAt = messageEdit.createdAt,
                            body = messageEdit.body,
                        )
                    },
            )
        }
    }
}

data class EmojiReaction(
    val reactedAt: Instant,
    val creator: Identity,
    val emojiSequence: String,
    val readAt: Instant?,
)

data class MessageEdit(
    /**
     * This is the timestamp where an edit has been created.
     */
    val createdAt: ULong,

    /**
     * This is the body after the edit.
     */
    val body: String,
)

data class MessageStateConversionResult(
    val messageModel: MessageModel,
    val messageHistoryEntries: List<MessageHistoryEntry>,
    val emojiReactions: List<EmojiReaction>,
)
