package ch.threema.app.libthreema.helper

import ch.threema.app.libthreema.conversion.EmojiReaction
import ch.threema.app.libthreema.conversion.MessageStateConversionResult
import ch.threema.app.libthreema.conversion.MessageStateConverter
import ch.threema.app.libthreema.conversion.toGroupIdentity
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.sumOfSizes
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.MessageHistoryEntry
import ch.threema.data.repositories.EditHistoryRepository
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.data.storage.DbEmojiReaction
import ch.threema.data.storage.EmojiReactionsDao
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.libthreema.ConversationId
import ch.threema.libthreema.MessageState as LibthreemaMessageState
import ch.threema.storage.factories.ContactMessageModelFactory
import ch.threema.storage.factories.DistributionListMessageModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel

private val logger = getThreemaLogger("MessageStateDatabaseWriter")

class MessageStateDatabaseWriter(
    private val contactMessageModelFactory: ContactMessageModelFactory,
    private val groupMessageModelFactory: GroupMessageModelFactory,
    private val distributionListMessageModelFactory: DistributionListMessageModelFactory,
    private val editHistoryRepository: EditHistoryRepository,
    private val emojiReactionsRepository: EmojiReactionsRepository,
    private val messageStateConverter: MessageStateConverter,
) {

    /**
     * Insert the messages in the same order as they appear into the database.
     *
     * @throws IllegalStateException in case we can't find a [GroupDatabaseId] for a given [GroupIdentity]
     */
    fun writeMessageStatesIntoDatabase(
        messageStates: List<LibthreemaMessageState>,
        groupDatabaseIdsByGroupIdentities: Map<GroupIdentity, GroupDatabaseId>,
    ) {
        if (messageStates.isEmpty()) {
            return
        }
        val convertedMessageStates = messageStates.map { messageState ->
            when (val conversationId = messageState.conversationId) {
                is ConversationId.Contact -> messageStateConverter.convertContactMessage(
                    contactMessageState = messageState,
                )
                is ConversationId.Group -> messageStateConverter.convertGroupMessage(
                    groupMessageState = messageState,
                    groupDatabaseId = groupDatabaseIdsByGroupIdentities.getValue(conversationId.v1.toGroupIdentity()),
                )
                is ConversationId.DistributionList -> messageStateConverter.convertDistributionListMessage(
                    distributionListMessageState = messageState,
                )
            }
        }

        writeMessageModelsToDatabase(
            messageModels = convertedMessageStates
                .mapNotNull(MessageStateConversionResult::messageModel),
        )

        writeMessageHistoryEntriesToDatabase(
            messageHistoryEntriesByMessage = convertedMessageStates.getMessageHistoryEntriesByMessage(),
        )

        writeEmojiReactionsToDatabase(
            emojiReactionsByMessage = convertedMessageStates.getEmojiReactionsByMessage(),
        )
    }

    private fun writeMessageModelsToDatabase(messageModels: List<MessageModel>) {
        if (messageModels.isEmpty()) {
            return
        }
        logger.info("Writing {} message models to database", messageModels.size)

        contactMessageModelFactory.create(
            messageModels.filterIsInstance<ContactMessageModel>(),
        )

        groupMessageModelFactory.create(
            messageModels.filterIsInstance<GroupMessageModel>(),
        )

        distributionListMessageModelFactory.create(
            messageModels.filterIsInstance<DistributionListMessageModel>(),
        )

        messageModels
            .filterNot { messageModel ->
                messageModel is ContactMessageModel ||
                    messageModel is GroupMessageModel ||
                    messageModel is DistributionListMessageModel
            }
            .forEach { messageModel ->
                logger.warn("Unexpected message model type: {}", messageModel.javaClass)
            }
    }

    private fun writeMessageHistoryEntriesToDatabase(messageHistoryEntriesByMessage: Map<MessageModel, List<MessageHistoryEntry>>) {
        logger.info(
            "Writing {} message revisions for {} messages",
            messageHistoryEntriesByMessage.values.sumOfSizes(),
            messageHistoryEntriesByMessage.size,
        )
        val contactMessagesHistoryEntries = messageHistoryEntriesByMessage
            .mapNotNull { (messageModel, historyEntries) -> (messageModel as? ContactMessageModel)?.let { it to historyEntries } }
            .toMap()
        val groupMessagesHistoryEntries = messageHistoryEntriesByMessage
            .mapNotNull { (messageModel, historyEntries) -> (messageModel as? GroupMessageModel)?.let { it to historyEntries } }
            .toMap()

        editHistoryRepository.createEntries(
            contactMessagesHistoryEntries = contactMessagesHistoryEntries,
            groupMessagesHistoryEntries = groupMessagesHistoryEntries,
        )
    }

    private fun writeEmojiReactionsToDatabase(emojiReactionsByMessage: Map<MessageModel, List<EmojiReaction>>) {
        if (emojiReactionsByMessage.isEmpty()) {
            return
        }
        logger.info("Writing {} emoji reactions for {} messages", emojiReactionsByMessage.values.sumOfSizes(), emojiReactionsByMessage.size)

        // Restore emoji reactions for contact messages
        emojiReactionsRepository.restoreContactReactions { insertHandle ->
            emojiReactionsByMessage
                .filterKeys { messageModel -> messageModel is ContactMessageModel }
                .forEach { (messageModel, emojiReactions) ->
                    insertHandle.insert(messageModel, emojiReactions)
                }
        }

        // Restore emoji reactions for group messages
        emojiReactionsRepository.restoreGroupReactions { insertHandle ->
            emojiReactionsByMessage
                .filterKeys { messageModel -> messageModel is GroupMessageModel }
                .forEach { (messageModel, emojiReactions) ->
                    insertHandle.insert(messageModel, emojiReactions)
                }
        }
    }

    private fun EmojiReactionsDao.ReactionInsertHandle.insert(messageModel: MessageModel, emojiReactions: List<EmojiReaction>) {
        emojiReactions.forEach { emojiReaction ->
            insert(
                DbEmojiReaction(
                    messageId = messageModel.id,
                    senderIdentity = emojiReaction.creator.value,
                    emojiSequence = emojiReaction.emojiSequence,
                    reactedAt = emojiReaction.reactedAt,
                    readAt = emojiReaction.readAt,
                ),
            )
        }
    }

    private fun List<MessageStateConversionResult>.getMessageHistoryEntriesByMessage(): Map<MessageModel, List<MessageHistoryEntry>> =
        associate { (messageModel, messageHistoryEntries, _) ->
            messageModel to messageHistoryEntries
        }

    private fun List<MessageStateConversionResult>.getEmojiReactionsByMessage(): Map<MessageModel, List<EmojiReaction>> =
        associate { (messageModel, _, emojiReactions) -> messageModel to emojiReactions }
}
