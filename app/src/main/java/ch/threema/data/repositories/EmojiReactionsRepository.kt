package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.emojis.EmojiUtil
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.MessageService
import ch.threema.app.utils.ThrowingConsumer
import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.data.IdentityProvider
import ch.threema.data.ModelTypeCache
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.models.toDataType
import ch.threema.data.storage.DbEmojiReaction
import ch.threema.data.storage.EmojiReactionsDao
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import java.time.Instant
import org.koin.mp.KoinPlatform

private val logger = getThreemaLogger("EmojiReactionsRepository")

@SessionScoped
class EmojiReactionsRepository(
    private val cache: ModelTypeCache<ReactionMessageIdentifier, EmojiReactionsModel>,
    private val emojiReactionDao: EmojiReactionsDao,
    private val identityProvider: IdentityProvider,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskManager: TaskManager,
    private val timeProvider: TimeProvider,
) {
    private val myIdentity by lazy { identityProvider.requireIdentityString() }

    // TODO(ANDR-3325): Remove message service
    private val messageService: MessageService by KoinPlatform.getKoin().inject()

    /**
     * Get all reactions for a message, including reactions from the old ack/dec system
     */
    fun getReactionsByMessage(messageModel: MessageModel): EmojiReactionsModel? {
        logger.debug("Loading emoji reactions for message {}", messageModel.id)
        synchronized(cache) {
            val reactionMessageIdentifier = ReactionMessageIdentifier.fromMessageModel(messageModel)
                ?: return null
            return cache.getOrCreate(reactionMessageIdentifier) {
                val dbReactions = emojiReactionDao.findAllByMessage(messageModel)
                    .map(DbEmojiReaction::toDataType)
                val allReactions = addAckDecReactions(messageModel, dbReactions.toMutableList())
                EmojiReactionsModel(allReactions, multiDeviceManager, taskManager)
            }
        }
    }

    fun <T : MessageModel> getReactionsByMessages(messageModels: List<T>): Map<T, EmojiReactionsModel> {
        if (messageModels.isEmpty()) {
            return emptyMap()
        }
        logger.debug("Loading emoji reactions for {} messages", messageModels.size)
        synchronized(cache) {
            val result = mutableMapOf<T, EmojiReactionsModel>()
            val identifierAndMessageModelsWithoutCachedReactions = mutableListOf<Pair<ReactionMessageIdentifier, T>>()
            messageModels.forEach { messageModel ->
                val reactionMessageIdentifier = ReactionMessageIdentifier.fromMessageModel(messageModel)
                    ?: return@forEach
                val cachedReactionsModel = cache[reactionMessageIdentifier]
                if (cachedReactionsModel != null) {
                    result[messageModel] = cachedReactionsModel
                } else {
                    identifierAndMessageModelsWithoutCachedReactions.add(Pair(reactionMessageIdentifier, messageModel))
                }
            }

            if (identifierAndMessageModelsWithoutCachedReactions.isEmpty()) {
                return result
            }

            identifierAndMessageModelsWithoutCachedReactions
                .groupBy { (identifier, _) ->
                    // We group by message type here such that contact messages and group messages are handled separately,
                    // as DbEmojiReaction lacks globally a unique identifier.
                    identifier.messageType
                }
                .forEach { (_, identifierAndMessageModels) ->
                    val reactionsByMessageId = identifierAndMessageModels
                        .map { (_, messageModel) -> messageModel }
                        .let(emojiReactionDao::findAllByMessages)
                        .groupBy(DbEmojiReaction::messageId)
                    identifierAndMessageModels.forEach { (reactionMessageIdentifier, messageModel) ->
                        val dbReactions = reactionsByMessageId[messageModel.id]
                            ?.map(DbEmojiReaction::toDataType)
                        val allReactions = addAckDecReactions(messageModel, dbReactions?.toMutableList() ?: mutableListOf())
                        val reactionsModel = EmojiReactionsModel(allReactions, multiDeviceManager, taskManager)
                        cache[reactionMessageIdentifier] = reactionsModel
                        result[messageModel] = reactionsModel
                    }
                }

            return result
        }
    }

    /**
     * Get all reactions for a message, including reactions from the old ack/dec system
     * Returns an empty list if there are no reactions
     */
    fun safeGetReactionsByMessage(messageModel: MessageModel): List<EmojiReactionData> {
        return getReactionsByMessage(messageModel)?.data ?: emptyList()
    }

    fun deleteAllReactionsForMessage(messageModel: MessageModel) {
        logger.debug("Delete all for message with uid {}", messageModel.uid)
        emojiReactionDao.deleteAllByMessage(messageModel)
        ReactionMessageIdentifier.fromMessageModel(messageModel)?.let { reactionMessageIdentifier ->
            cache[reactionMessageIdentifier]?.clear()
            cache.remove(reactionMessageIdentifier)
        }
    }

    /**
     * This deletes all reactions from the database.
     */
    fun deleteAllReactions() {
        emojiReactionDao.deleteAll()
    }

    /**
     * Mark all unread reactions of the conversation identified by [conversationId] as read.
     *
     * Distribution list conversations cannot have reactions, so this is a no-op for them.
     */
    fun markConversationReactionsAsRead(conversationId: ConversationId) {
        val readAt = timeProvider.get()
        val targetMessageType: ReactionMessageIdentifier.TargetMessageType
        val affectedMessageIds: Set<Int>
        when (conversationId) {
            is ContactConversationId -> {
                targetMessageType = ReactionMessageIdentifier.TargetMessageType.ONE_TO_ONE
                affectedMessageIds = emojiReactionDao.markUnreadReactionsAsReadForContactConversation(
                    contactConversationId = conversationId,
                    readAt = readAt,
                )
            }
            is GroupConversationId -> {
                targetMessageType = ReactionMessageIdentifier.TargetMessageType.GROUP
                affectedMessageIds = emojiReactionDao.markUnreadReactionsAsReadForGroupConversation(
                    groupConversationId = conversationId,
                    readAt = readAt,
                )
            }
            is DistributionListConversationId -> return
        }
        if (affectedMessageIds.isEmpty()) {
            return
        }
        synchronized(cache) {
            affectedMessageIds.forEach { messageId ->
                cache.remove(
                    ReactionMessageIdentifier(
                        messageId = messageId,
                        messageType = targetMessageType,
                    ),
                )
            }
        }
        logger.debug("Marked reactions of {} messages in conversation {} as read", affectedMessageIds.size, conversationId)
    }

    fun getContactReactionsCount(): Long =
        emojiReactionDao.getContactReactionsCount()

    fun getGroupReactionsCount(): Long =
        emojiReactionDao.getGroupReactionsCount()

    /**
     * This method is only intended for backup creation.
     * Iteration is ordered by id of the referenced messages.
     */
    fun iterateAllContactReactionsForBackup(consumer: ThrowingConsumer<EmojiReactionsDao.BackupContactReaction>) {
        emojiReactionDao.iterateAllContactBackupReactions(consumer)
    }

    /**
     * This method is only intended for backup creation.
     * Iteration is ordered by id of the referenced messages.
     */
    fun iterateAllGroupReactionsForBackup(consumer: ThrowingConsumer<EmojiReactionsDao.BackupGroupReaction>) {
        emojiReactionDao.iterateAllGroupBackupReactions(consumer)
    }

    /**
     * Add reactions from the old ack/dec system to an existing list of emoji reactions
     */
    private fun addAckDecReactions(
        targetMessageModel: MessageModel,
        mutableEmojiReactions: MutableList<EmojiReactionData>,
    ): List<EmojiReactionData> {
        if (targetMessageModel is GroupMessageModel) {
            mutableEmojiReactions += targetMessageModel.groupMessageStates?.mapNotNull { (identity, reaction) ->
                when (reaction) {
                    MessageState.USERACK.toString() -> createEmojiReactionData(
                        messageModel = targetMessageModel,
                        senderIdentity = identity,
                        emojiSequence = EmojiUtil.THUMBS_UP_SEQUENCE,
                    )

                    MessageState.USERDEC.toString() -> createEmojiReactionData(
                        messageModel = targetMessageModel,
                        senderIdentity = identity,
                        emojiSequence = EmojiUtil.THUMBS_DOWN_SEQUENCE,
                    )

                    else -> null
                }
            } ?: emptyList()
        } else {
            // in case of an outgoing message only the other party can react - and vice versa
            val senderIdentity = if (targetMessageModel.isOutbox) targetMessageModel.identity!! else myIdentity
            val state = targetMessageModel.state

            when (state) {
                MessageState.USERACK -> mutableEmojiReactions += createEmojiReactionData(
                    messageModel = targetMessageModel,
                    senderIdentity = senderIdentity,
                    emojiSequence = EmojiUtil.THUMBS_UP_SEQUENCE,
                )

                MessageState.USERDEC -> mutableEmojiReactions += createEmojiReactionData(
                    messageModel = targetMessageModel,
                    senderIdentity = senderIdentity,
                    emojiSequence = EmojiUtil.THUMBS_DOWN_SEQUENCE,
                )

                else -> {
                    // ignore
                }
            }
        }
        return mutableEmojiReactions.toList()
    }

    private fun createEmojiReactionData(
        messageModel: MessageModel,
        senderIdentity: IdentityString,
        emojiSequence: String,
    ) = EmojiReactionData(
        messageId = messageModel.id,
        senderIdentity = senderIdentity,
        emojiSequence = emojiSequence,
        reactedAt = messageModel.createdAt!!,
        readAt = messageModel.readAt,
    )

    /**
     * Inserts a [DbEmojiReaction] into the db.
     *
     * @param targetMessage The message model to create a reaction to
     * @param senderIdentity The identity of the sender of the reaction to create
     * @param emojiSequence The emoji codepoint sequence of the reaction
     *
     * @throws EmojiReactionEntryCreateException if inserting the [DbEmojiReaction] in the database failed
     * @throws IllegalStateException if the [targetMessage] is not valid to create a [DbEmojiReaction] from
     *
     */
    @Throws(EmojiReactionEntryCreateException::class, IllegalStateException::class)
    fun createEntry(
        targetMessage: MessageModel,
        senderIdentity: IdentityString,
        emojiSequence: String,
        reactedAt: Instant,
    ) {
        synchronized(cache) {
            try {
                val reactionEntry = DbEmojiReaction(
                    messageId = targetMessage.id,
                    senderIdentity = senderIdentity,
                    emojiSequence = emojiSequence,
                    reactedAt = reactedAt,
                    readAt = null,
                )
                emojiReactionDao.create(reactionEntry, targetMessage)
                ReactionMessageIdentifier.fromMessageModel(targetMessage)
                    ?.let { reactionMessageIdentifier ->
                        cache[reactionMessageIdentifier]?.addEntry(reactionEntry.toDataType())
                    }
            } catch (exception: SQLiteException) {
                throw EmojiReactionEntryCreateException(exception)
            }
        }
    }

    @Throws(Exception::class)
    fun restoreContactReactions(block: EmojiReactionsDao.TransactionalReactionInsertScope) {
        emojiReactionDao.insertContactReactionsInTransaction(block)
    }

    @Throws(Exception::class)
    fun restoreGroupReactions(block: EmojiReactionsDao.TransactionalReactionInsertScope) {
        emojiReactionDao.insertGroupReactionsInTransaction(block)
    }

    /**
     * Removes a [DbEmojiReaction] from the db.
     * Call this before saving the edited message
     *
     * @param targetMessage The message model to remove the reaction from
     * @param senderIdentity The identity of the sender of the reaction to remove from the db
     * @param emojiSequence The emoji codepoint sequence of the reaction
     *
     * @throws EmojiReactionEntryRemoveException if removing the [DbEmojiReaction] from the database failed
     * @throws IllegalStateException if the [targetMessage] is not valid
     *
     */
    @Throws(EmojiReactionEntryRemoveException::class, IllegalStateException::class)
    fun removeEntry(
        targetMessage: MessageModel,
        senderIdentity: IdentityString,
        emojiSequence: String,
    ) {
        synchronized(cache) {
            try {
                val reactionEntry = DbEmojiReaction(
                    messageId = targetMessage.id,
                    senderIdentity = senderIdentity,
                    emojiSequence = emojiSequence,
                    reactedAt = Instant.now(),
                    readAt = null,
                )

                emojiReactionDao.remove(reactionEntry, targetMessage)

                // TODO(ANDR-3325): Remove ACK/DEC compatibility
                val isThumbsUp = EmojiUtil.isThumbsUpEmoji(emojiSequence)
                val isThumbsDown = EmojiUtil.isThumbsDownEmoji(emojiSequence)

                if (isThumbsUp || isThumbsDown) {
                    // if the existing reaction is an ackji (e.g. received earlier, before we upgraded) and one and more
                    // recipients support emoji reactions, we need to clear the ackji from our database to reflect
                    // the withdrawal in the UI (because we sent at least some of the users a WITHDRAW action)
                    //
                    // however, since it is not possible to withdraw ackjis, old clients will not receive a change
                    // which will inadvertently lead to inconsistencies
                    val messageState: MessageState? = targetMessage.state
                    if ((messageState == MessageState.USERACK && isThumbsUp) || (messageState == MessageState.USERDEC && isThumbsDown)) {
                        messageService.clearMessageState(targetMessage)
                    }
                }
                ReactionMessageIdentifier.fromMessageModel(targetMessage)
                    ?.let { reactionMessageIdentifier ->
                        cache[reactionMessageIdentifier]?.removeEntry(reactionEntry.toDataType())
                    }
            } catch (exception: SQLiteException) {
                throw EmojiReactionEntryRemoveException(exception)
            }
        }
    }

    /**
     *  We need to use this compound identifier object for emoji reactions because
     *  the [MessageModel.id] is **not** unique across different message type db tables.
     *
     *  @param messageId Represents the local auto-incremented message row-id ([MessageModel.id])
     *  @param messageType Is either [TargetMessageType.ONE_TO_ONE] or [TargetMessageType.GROUP]. Other existing
     *  message types like for example distribution-list messages can not receive emoji reactions.
     */
    data class ReactionMessageIdentifier(
        val messageId: Int,
        val messageType: TargetMessageType,
    ) {
        companion object {
            /**
             *  @return The reaction-message-identifier object or `null` if the concrete
             *  type of [MessageModel] can not receive emoji reactions.
             */
            fun fromMessageModel(messageModel: MessageModel): ReactionMessageIdentifier? {
                val messageType: TargetMessageType = when (messageModel) {
                    is ContactMessageModel -> TargetMessageType.ONE_TO_ONE
                    is GroupMessageModel -> TargetMessageType.GROUP
                    is DistributionListMessageModel -> return null
                }
                return ReactionMessageIdentifier(
                    messageId = messageModel.id,
                    messageType = messageType,
                )
            }
        }

        enum class TargetMessageType { ONE_TO_ONE, GROUP }
    }
}

class EmojiReactionEntryCreateException(e: Exception) :
    ThreemaException("Failed to create emoji reaction in the db", e)

class EmojiReactionEntryRemoveException(e: Exception) :
    ThreemaException("Failed to remove emoji reaction from the db", e)
