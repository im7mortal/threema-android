package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.conversion.toGroupIdentity
import ch.threema.app.libthreema.helper.MessageStateDatabaseReader
import ch.threema.app.libthreema.helper.MessageStateDatabaseWriter
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.MessageService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.data.datatypes.ContactMessageIdentifier
import ch.threema.data.datatypes.DistributionListMessageIdentifier
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupMessageIdentifier
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.libthreema.ConversationId
import ch.threema.libthreema.ConversationProvider as LibthreemaConversationProvider
import ch.threema.libthreema.ConversationType as LibthreemaConversationType
import ch.threema.libthreema.MessageId as LibthreemaMessageId
import ch.threema.libthreema.MessageState as LibthreemaMessageState
import ch.threema.libthreema.MessageStateMetadata
import ch.threema.libthreema.MessageStateUpdate
import ch.threema.libthreema.ProviderException
import ch.threema.libthreema.ThreemaId as LibthreemaThreemaId

private val logger = getThreemaLogger("ConversationProvider")

class ConversationProvider(
    private val messageStateDatabaseReader: MessageStateDatabaseReader,
    private val messageStateDatabaseWriter: MessageStateDatabaseWriter,
    override val guaranteedIdentityProvider: GuaranteedIdentityProvider,
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val distributionListService: DistributionListService,
    private val messageService: MessageService,
) : LibthreemaConversationProvider, IdentityAwareProvider() {

    override fun addMessages(messages: List<LibthreemaMessageState>) {
        withTimeMeasurement(logger, "addMessages", messages.size) {
            logger.info("Adding {} message states", messages.size)

            // Validate that all contacts from 1 to 1 messages exist and have the correct acquaintance level
            val allContactMessageIdentities = messages
                .mapNotNull { message -> (message.conversationId as? ConversationId.Contact)?.v1 }
                .toSet()
            if (!contactModelRepository.allExistWithAcquaintanceLevel(allContactMessageIdentities, AcquaintanceLevel.DIRECT)) {
                throw ProviderException.ContractViolation(
                    "At least one contact from a 1 to 1 message does not exist or does not have acquaintance level direct",
                )
            }

            // Validate that all groups from group messages exist
            val allGroupIdentities = messages
                .mapNotNull { message -> (message.conversationId as? ConversationId.Group)?.v1?.toGroupIdentity() }
                .toSet()
            if (!groupModelRepository.allExist(allGroupIdentities)) {
                throw ProviderException.ContractViolation("At least one group from a group message does not exist")
            }

            // Validate that all distribution lists from distribution list messages exist
            val allDistListIds = messages
                .mapNotNull { message -> (message.conversationId as? ConversationId.DistributionList)?.v1?.toLong() }
                .toSet()
            if (!distributionListService.allExist(allDistListIds)) {
                throw ProviderException.ContractViolation(
                    "At least one distribution list from a distribution list message does not exist",
                )
            }

            // Prepare list of all relevant group database ids for the GroupMessageModel
            val groupDatabaseIdsByGroupIdentities: Map<GroupIdentity, GroupDatabaseId> =
                groupModelRepository.getGroupDatabaseIdsByGroupIdentities(allGroupIdentities)
            check(groupDatabaseIdsByGroupIdentities.size == allGroupIdentities.size) {
                "Missing at least one internal group database id for a given group identity"
            }

            // Validate that all given messages are new messages (do not exist locally)
            if (anyMessageAlreadyExists(messages, groupDatabaseIdsByGroupIdentities)) {
                throw ProviderException.ContractViolation("At least one message already exists")
            }

            messageStateDatabaseWriter.writeMessageStatesIntoDatabase(
                messageStates = messages,
                groupDatabaseIdsByGroupIdentities = groupDatabaseIdsByGroupIdentities,
            )
        }
    }

    override fun setTypingIndicator(senderIdentity: LibthreemaThreemaId, isTyping: Boolean) {
        // TODO(ANDR-4689): This is only used once we do message processing with libthreema
        throw NotImplementedError()
    }

    override fun updateMessages(updates: List<MessageStateUpdate>) {
        // TODO(ANDR-4689): This is only used once we do message processing with libthreema
        throw NotImplementedError()
    }

    override fun getManyMessages(conversationType: LibthreemaConversationType, batchIndex: ULong): List<LibthreemaMessageState> =
        withTimeMeasurement(logger, "getManyMessages") {
            val messageStates = when (conversationType) {
                LibthreemaConversationType.CONTACT -> messageStateDatabaseReader.readContactMessageBatch(batchIndex = batchIndex)
                LibthreemaConversationType.GROUP -> messageStateDatabaseReader.readGroupMessageBatch(batchIndex = batchIndex)
                LibthreemaConversationType.DISTRIBUTION_LIST -> messageStateDatabaseReader.readDistributionListMessage(batchIndex = batchIndex)
            }

            logger.info(
                "Returning {} message states of batch {} for conversation type {}",
                messageStates.size,
                batchIndex,
                conversationType,
            )

            messageStates
        }

    override fun getMessagesMetadata(
        conversationId: ConversationId,
        messageIds: List<LibthreemaMessageId>,
    ): List<MessageStateMetadata> {
        // TODO(ANDR-4689): This is only used once we do message processing with libthreema
        throw NotImplementedError()
    }

    override fun isMessageMarkedUsed(senderIdentity: LibthreemaThreemaId, messageId: LibthreemaMessageId): Boolean {
        // TODO(ANDR-4689): This is only used once we do message processing with libthreema
        throw NotImplementedError()
    }

    /**
     * @throws NoSuchElementException if [groupDatabaseIdsByGroupIdentities] is missing a [GroupDatabaseId] value for a given [GroupIdentity]
     */
    private fun anyMessageAlreadyExists(
        messages: List<LibthreemaMessageState>,
        groupDatabaseIdsByGroupIdentities: Map<GroupIdentity, GroupDatabaseId>,
    ): Boolean {
        val messageIdentifiers = messages.map { message ->
            val messageId = MessageId(message.messageId.toLong())
            when (val conversationId = message.conversationId) {
                is ConversationId.Contact -> ContactMessageIdentifier(
                    messageId = messageId,
                    identity = conversationId.v1,
                )
                is ConversationId.Group -> GroupMessageIdentifier(
                    messageId = messageId,
                    groupDatabaseId = groupDatabaseIdsByGroupIdentities.getValue(conversationId.v1.toGroupIdentity()),
                )
                is ConversationId.DistributionList -> DistributionListMessageIdentifier(
                    messageId = messageId,
                    distributionListId = conversationId.v1.toLong(),
                )
            }
        }
        return messageService.anyExists(messageIdentifiers.toSet())
    }
}
