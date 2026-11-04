package ch.threema.data.storage

import android.database.sqlite.SQLiteException
import ch.threema.app.utils.ThrowingConsumer
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.MessageModel
import java.time.Instant

interface EmojiReactionsDao {
    /**
     * Insert a new emoji reaction
     *
     * @param entry The entry to add for the reaction
     * @param messageModel The message referenced by the reaction entry
     *
     * @throws SQLiteException if insertion fails due to a conflict
     * @throws ch.threema.data.repositories.EmojiReactionEntryCreateException if inserting the [DbEmojiReaction] in the database failed
     */
    fun create(entry: DbEmojiReaction, messageModel: MessageModel)

    /**
     * Remove an emoji reaction from the database
     */
    fun remove(entry: DbEmojiReaction, messageModel: MessageModel)

    /**
     * Delete all reactions referred to by the specified message
     */
    fun deleteAllByMessage(messageModel: MessageModel)

    /**
     * Find all reactions referred to by the specified message
     */
    fun findAllByMessage(messageModel: MessageModel): List<DbEmojiReaction>

    /**
     * Find all reactions referred to by the specified messages
     */
    fun findAllByMessages(messageModels: List<MessageModel>): List<DbEmojiReaction>

    /**
     * Mark all unread reactions of the contact conversation as read at [readAt].
     *
     * @return The ids of the messages that had unread reactions marked as read
     */
    fun markUnreadReactionsAsReadForContactConversation(contactConversationId: ContactConversationId, readAt: Instant): Set<Int>

    /**
     * Mark all unread reactions of the group conversation as read at [readAt].
     *
     * @return The ids of the messages that had unread reactions marked as read
     */
    fun markUnreadReactionsAsReadForGroupConversation(groupConversationId: GroupConversationId, readAt: Instant): Set<Int>

    /**
     * Delete all reactions from the database.
     */
    fun deleteAll()

    fun getContactReactionsCount(): Long

    fun getGroupReactionsCount(): Long

    /**
     * Iteration is ordered by the id of the referenced messages.
     */
    fun iterateAllContactBackupReactions(consumer: ThrowingConsumer<BackupContactReaction>)

    /**
     * Iteration is ordered by the id of the referenced messages.
     */
    fun iterateAllGroupBackupReactions(consumer: ThrowingConsumer<BackupGroupReaction>)

    fun insertContactReactionsInTransaction(block: TransactionalReactionInsertScope)

    fun insertGroupReactionsInTransaction(block: TransactionalReactionInsertScope)

    data class BackupContactReaction(
        val contactIdentity: IdentityString,
        val apiMessageId: String,
        val senderIdentity: IdentityString,
        val emojiSequence: String,
        val reactedAt: Long,
    )

    data class BackupGroupReaction(
        val apiGroupId: String,
        val groupCreatorIdentity: IdentityString,
        val apiMessageId: String,
        val senderIdentity: IdentityString,
        val emojiSequence: String,
        val reactedAt: Long,
    )

    fun interface ReactionInsertHandle {
        fun insert(entry: DbEmojiReaction)
    }

    fun interface TransactionalReactionInsertScope {
        @Throws(Exception::class)
        fun runInserts(handle: ReactionInsertHandle)
    }
}
