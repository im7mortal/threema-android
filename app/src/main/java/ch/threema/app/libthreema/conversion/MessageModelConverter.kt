package ch.threema.app.libthreema.conversion

import ch.threema.app.libthreema.extensions.getLibthreemaMessageDirection
import ch.threema.app.libthreema.extensions.getLibthreemaMessageStarMarker
import ch.threema.data.IdentityProvider
import ch.threema.data.models.EditHistoryEntryData
import ch.threema.data.models.EditHistoryListModel
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.libthreema.ConversationId as LibthreemaConversationId
import ch.threema.libthreema.DeletedMessageState as LibthreemaDeletedMessageState
import ch.threema.libthreema.MessageState as LibthreemaMessageState
import ch.threema.libthreema.MessageStateBody as LibthreemaMessageStateBody
import ch.threema.libthreema.MessageStateBodyType as LibthreemaMessageStateBodyType
import ch.threema.libthreema.PollClose as LibthreemaPollClose
import ch.threema.libthreema.PollOpen as LibthreemaPollOpen
import ch.threema.storage.factories.PollModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.PollDataModel

/**
 * This converter can be used to convert an [MessageModel] to a [LibthreemaMessageState].
 */
class MessageModelConverter(
    private val groupModelRepository: GroupModelRepository,
    private val pollModelFactory: PollModelFactory,
    private val identityProvider: IdentityProvider,
    private val textMessageBodyConverter: TextMessageBodyConverter,
    private val locationMessageBodyConverter: LocationMessageBodyConverter,
) {
    /**
     * Convert the message model to a libthreema message state.
     *
     * @throws [MessageModelConversionException] if the message model cannot be converted to its corresponding libthreema type
     */
    @Throws(MessageModelConversionException::class)
    fun convertToLibthreemaMessageState(
        messageModel: MessageModel,
        emojiReactions: EmojiReactionsModel?,
        editHistoryListModel: EditHistoryListModel?,
    ): LibthreemaMessageState {
        // Note that we need to convert the message body state before we abort because the message id is missing. Otherwise, a status message would
        // result in a `MissingMessageId` exception instead of a `MessageTypeNotIncluded` exception.
        val messageBodyState = getMessageStateBody(messageModel, editHistoryListModel?.data)
        val creatorIdentity = if (messageModel.isOutbox) {
            identityProvider.getIdentityString() ?: throw MessageModelConversionException.InvalidState()
        } else {
            messageModel.identity ?: throw MessageModelConversionException.MissingIdentity()
        }
        val messageId = messageModel.messageId?.messageIdLong?.toULong() ?: throw MessageModelConversionException.MissingMessageId()

        return LibthreemaMessageState(
            conversationId = getLibthreemaConversationId(messageModel),
            creatorIdentity = creatorIdentity,
            messageId = messageId,
            createdAt = messageModel.createdAt?.toEpochMilli()?.toULong() ?: 0u, // TODO(SE-609)
            direction = messageModel.getLibthreemaMessageDirection(),
            readAt = messageModel.readAt?.toEpochMilli()?.toULong(), // TODO(SE-609)
            starMarker = messageModel.getLibthreemaMessageStarMarker(),
            reactions = emojiReactions.convertToLibthreemaReactions(),
            body = messageBodyState,
        )
    }

    private fun getLibthreemaConversationId(messageModel: MessageModel): LibthreemaConversationId = when (messageModel) {
        is ContactMessageModel -> LibthreemaConversationId.Contact(
            v1 = messageModel.identity ?: error("Cannot get conversation id for message model without identity"),
        )
        is GroupMessageModel -> LibthreemaConversationId.Group(
            v1 = groupModelRepository.getByGroupDatabaseId(messageModel.groupId.toLong())
                ?.groupIdentity
                ?.toLibthreemaGroupIdentity()
                ?: error("Group not found for message"),
        )
        is DistributionListMessageModel -> LibthreemaConversationId.DistributionList(
            v1 = messageModel.distributionListId.toULong(),
        )
    }

    private fun getMessageStateBody(
        messageModel: MessageModel,
        editHistoryEntries: List<EditHistoryEntryData>?,
    ): LibthreemaMessageStateBody {
        messageModel.deletedAt?.let { deletedAt ->
            return LibthreemaMessageStateBody.Deleted(
                v1 = LibthreemaDeletedMessageState(
                    previousMessageType = messageModel.type
                        ?.toLibthreemaMessageStateBodyType()
                        ?: LibthreemaMessageStateBodyType.DELETED,
                    deletedAt = deletedAt.toEpochMilli().toULong(),
                ),
            )
        }

        return when (messageModel.type) {
            MessageType.TEXT -> LibthreemaMessageStateBody.Text(
                textMessageBodyConverter.convertToLibthreemaTextMessageState(
                    messageModel = messageModel,
                    editHistoryEntries = editHistoryEntries,
                ),
            )
            MessageType.LOCATION -> LibthreemaMessageStateBody.Location(
                locationMessageBodyConverter.convertToLibthreemaLocationMessage(
                    messageModel,
                ),
            )
            MessageType.STATUS -> throw MessageModelConversionException.MessageTypeNotIncluded()
            MessageType.POLL -> getPollMessageBodyState(messageModel)
            // TODO(ANDR-4859): Support files
            MessageType.FILE -> throw MessageModelConversionException.MessageTypeNotIncluded()
            MessageType.VOIP_STATUS -> throw MessageModelConversionException.MessageTypeNotIncluded()
            MessageType.VIRTUAL -> throw MessageModelConversionException.MessageTypeNotIncluded()
            MessageType.GROUP_CALL_STATUS -> throw MessageModelConversionException.MessageTypeNotIncluded()
            MessageType.FORWARD_SECURITY_STATUS -> throw MessageModelConversionException.MessageTypeNotIncluded()
            MessageType.GROUP_STATUS -> throw MessageModelConversionException.MessageTypeNotIncluded()
            null -> throw MessageModelConversionException.UnknownMessageType()
        }
    }

    private fun getPollMessageBodyState(messageModel: MessageModel): LibthreemaMessageStateBody {
        val pollData = messageModel.pollData
        val apiPollId = pollModelFactory.getApiPollIdByLocalPollId(pollData.pollId)
            ?.hexToULong()
            ?: throw MessageModelConversionException.MissingPoll()
        return when (pollData.type) {
            PollDataModel.Type.POLL_CREATED -> LibthreemaMessageStateBody.PollOpen(LibthreemaPollOpen(apiPollId))
            PollDataModel.Type.POLL_CLOSED -> LibthreemaMessageStateBody.PollClose(LibthreemaPollClose(apiPollId))
            PollDataModel.Type.POLL_MODIFIED -> throw MessageModelConversionException.MessageTypeNotIncluded()
        }
    }

    private fun MessageType.toLibthreemaMessageStateBodyType() = when (this) {
        MessageType.TEXT -> LibthreemaMessageStateBodyType.TEXT
        MessageType.LOCATION -> LibthreemaMessageStateBodyType.LOCATION
        MessageType.STATUS -> null
        MessageType.POLL -> LibthreemaMessageStateBodyType.POLL_OPEN // Note that we use POLL_OPEN for any poll message as it doesn't matter.
        MessageType.FILE -> null
        MessageType.VOIP_STATUS -> null
        MessageType.VIRTUAL -> null
        MessageType.GROUP_CALL_STATUS -> null
        MessageType.FORWARD_SECURITY_STATUS -> null
        MessageType.GROUP_STATUS -> null
    }

    sealed class MessageModelConversionException : Throwable() {
        /**
         * There is no sender or recipient identity associated with the message.
         */
        class MissingIdentity : MessageModelConversionException()

        /**
         * There is no message id associated with the message.
         *
         * TODO(ANDR-4904): We need to remove this at some point. We should do a migration.
         */
        class MissingMessageId : MessageModelConversionException()

        /**
         * The message type is unknown.
         */
        class UnknownMessageType : MessageModelConversionException()

        /**
         * The message should not be included in the data backup as this message type should not be backed up.
         */
        class MessageTypeNotIncluded : MessageModelConversionException()

        /**
         * The message refers to a poll that doesn't exist
         */
        class MissingPoll : MessageModelConversionException()

        /**
         * The app is in an invalid state.
         */
        class InvalidState : MessageModelConversionException()
    }
}
