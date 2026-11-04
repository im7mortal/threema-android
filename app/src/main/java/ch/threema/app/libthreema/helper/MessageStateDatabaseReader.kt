package ch.threema.app.libthreema.helper

import ch.threema.app.libthreema.conversion.MessageModelConverter
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.EditHistoryListModel
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.EditHistoryRepository
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.libthreema.MessageState as LibthreemaMessageState
import ch.threema.storage.factories.ContactMessageModelFactory
import ch.threema.storage.factories.DistributionListMessageModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.supportsEmojiReactions

private val logger = getThreemaLogger("MessageStateDatabaseReader")

/**
 * This reader can be used to read message states from the database.
 */
class MessageStateDatabaseReader(
    private val contactMessageModelFactory: ContactMessageModelFactory,
    private val groupMessageModelFactory: GroupMessageModelFactory,
    private val distributionListMessageModelFactory: DistributionListMessageModelFactory,
    private val emojiReactionsRepository: EmojiReactionsRepository,
    private val editHistoryRepository: EditHistoryRepository,
    private val messageModelConverter: MessageModelConverter,
    private val batchSizeProvider: BatchSizeProvider,
) {

    private val contactMessageStateBatchProvider: (batchIndex: ULong) -> List<LibthreemaMessageState> by lazy {
        createContactMessageStateBatchProvider()
    }

    private val groupMessageStateBatchProvider: (batchIndex: ULong) -> List<LibthreemaMessageState> by lazy {
        createGroupMessageStateBatchProvider()
    }

    private val distributionListModelStateBatchProvider: (batchIndex: ULong) -> List<LibthreemaMessageState> by lazy {
        createDistributionListMessageStateBatchProvider()
    }

    private fun createContactMessageStateBatchProvider(): (batchIndex: ULong) -> List<LibthreemaMessageState> {
        val batchSize = batchSizeProvider.contactMessageBatchSize
        val messageModelList = ArrayList<ContactMessageModel>(batchSize)
        return { batchIndex ->
            messageModelList.clear()
            contactMessageModelFactory.loadMessageModelsIntoList(
                messageModelList,
                batchSize,
                batchIndex.toInt() * batchSize,
            )
            convertToLibthreemaMessageStates(messageModelList)
        }
    }

    private fun createGroupMessageStateBatchProvider(): (batchIndex: ULong) -> List<LibthreemaMessageState> {
        val batchSize = batchSizeProvider.groupMessageBatchSize
        val messageModelList = ArrayList<GroupMessageModel>(batchSize)
        return { batchIndex ->
            messageModelList.clear()
            groupMessageModelFactory.loadMessageModelsIntoList(
                messageModelList,
                batchSize,
                batchIndex.toInt() * batchSize,
            )
            convertToLibthreemaMessageStates(messageModelList)
        }
    }

    private fun createDistributionListMessageStateBatchProvider(): (batchIndex: ULong) -> List<LibthreemaMessageState> {
        val batchSize = batchSizeProvider.distributionListMessageBatchSize
        val messageModelList = ArrayList<DistributionListMessageModel>(batchSize)
        return { batchIndex ->
            messageModelList.clear()
            distributionListMessageModelFactory.loadMessageModelsIntoList(
                messageModelList,
                batchSize,
                batchIndex.toInt() * batchSize,
            )
            convertToLibthreemaMessageStates(messageModelList)
        }
    }

    private fun convertToLibthreemaMessageStates(messageModelList: List<MessageModel>): List<LibthreemaMessageState> {
        val emojiReactionsByMessage = emojiReactionsRepository.getReactionsByMessages(
            messageModelList.filter { it.type?.supportsEmojiReactions == true },
        )
        val editHistoryListModelsByMessageUid = editHistoryRepository.getByMessageUids(
            messageModelList
                .filter(MessageModel::supportsEmojiReactions)
                .mapNotNull(MessageModel::uid),
        )
        return messageModelList.mapNotNull { messageModel ->
            convertToLibthreemaMessageStateOrNull(
                messageModel = messageModel,
                emojiReactions = emojiReactionsByMessage[messageModel],
                editHistoryListModel = messageModel.uid?.let(editHistoryListModelsByMessageUid::get),
            )
        }
    }

    private fun convertToLibthreemaMessageStateOrNull(
        messageModel: MessageModel,
        emojiReactions: EmojiReactionsModel?,
        editHistoryListModel: EditHistoryListModel?,
    ): LibthreemaMessageState? = try {
        messageModelConverter.convertToLibthreemaMessageState(messageModel, emojiReactions, editHistoryListModel)
    } catch (e: MessageModelConverter.MessageModelConversionException) {
        when (e) {
            is MessageModelConverter.MessageModelConversionException.MessageTypeNotIncluded -> {
                // Just skip this as it is not relevant for the backup
                null
            }

            is MessageModelConverter.MessageModelConversionException.MissingIdentity -> {
                logger.warn("Missing identity for message model of type ${messageModel.type}", e)
                // TODO(ANDR-4890): For internal testing, we want this to fail and therefore we throw this exception. For the final release, we may
                //  want to be more defensive here and just log and skip the message.
                throw e
            }

            is MessageModelConverter.MessageModelConversionException.MissingMessageId -> {
                logger.error("Missing message id for message model of type ${messageModel.type}", e)
                // TODO(ANDR-4889): For internal testing, we want this to fail and therefore we throw this exception as discussed in ANDR-4719.
                throw e
            }

            is MessageModelConverter.MessageModelConversionException.UnknownMessageType -> {
                logger.error("Message type of message model is unknown", e)
                // TODO(ANDR-4890): For internal testing, we want this to fail and therefore we throw this exception.
                throw e
            }

            is MessageModelConverter.MessageModelConversionException.MissingPoll -> {
                logger.warn("Poll message refers to unknown poll, skipping it")
                null
            }

            is MessageModelConverter.MessageModelConversionException.InvalidState -> {
                logger.error("Invalid state while converting message")
                throw e
            }
        }
    }

    fun readContactMessageBatch(batchIndex: ULong): List<LibthreemaMessageState> =
        contactMessageStateBatchProvider(batchIndex)

    fun readGroupMessageBatch(batchIndex: ULong): List<LibthreemaMessageState> =
        groupMessageStateBatchProvider(batchIndex)

    fun readDistributionListMessage(batchIndex: ULong): List<LibthreemaMessageState> =
        distributionListModelStateBatchProvider(batchIndex)
}
