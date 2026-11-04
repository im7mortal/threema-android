package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.toEpochMilliInstant
import ch.threema.data.ModelTypeCache
import ch.threema.data.datatypes.EditHistoryType
import ch.threema.data.datatypes.LocalMessageId
import ch.threema.data.datatypes.MessageHistoryEntry
import ch.threema.data.models.EditHistoryListModel
import ch.threema.data.models.ModelDeletedException
import ch.threema.data.models.toDataType
import ch.threema.data.storage.DbEditHistoryEntry
import ch.threema.data.storage.EditHistoryDao
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import java.time.Instant

private val logger = getThreemaLogger("EditHistoryRepository")

@SessionScoped
class EditHistoryRepository(
    private val cache: ModelTypeCache<MessageUid, EditHistoryListModel>,
    private val editHistoryDao: EditHistoryDao,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskManager: TaskManager,
) {

    @Synchronized
    fun getByMessageUid(messageUid: MessageUid): EditHistoryListModel? =
        cache.getOrCreate(messageUid) {
            val dbEditHistoryEntries = editHistoryDao
                .findAllByMessageUid(messageUid)
                .map(DbEditHistoryEntry::toDataType)
            EditHistoryListModel(
                data = dbEditHistoryEntries,
                multiDeviceManager = multiDeviceManager,
                taskManager = taskManager,
            )
        }

    @Synchronized
    fun getByMessageUids(messageUids: Iterable<MessageUid>): Map<MessageUid, EditHistoryListModel> {
        val result = mutableMapOf<MessageUid, EditHistoryListModel>()
        val messageUidsMissingInCache = mutableListOf<MessageUid>()
        messageUids.forEach { messageUid ->
            val cachedEditHistoryListModel = cache[messageUid]
            if (cachedEditHistoryListModel != null) {
                result[messageUid] = cachedEditHistoryListModel
            } else {
                messageUidsMissingInCache.add(messageUid)
            }
        }
        if (messageUidsMissingInCache.isEmpty()) {
            return result
        }
        editHistoryDao.findAllByMessageUids(messageUidsMissingInCache)
            .forEach { (messageUid, editHistoryEntries) ->
                val editHistoryListModel = EditHistoryListModel(
                    data = editHistoryEntries.map(DbEditHistoryEntry::toDataType),
                    multiDeviceManager = multiDeviceManager,
                    taskManager = taskManager,
                )
                cache[messageUid] = editHistoryListModel
                result[messageUid] = editHistoryListModel
            }
        return result
    }

    /**
     * Inserts a [DbEditHistoryEntry] into the db.
     *
     * @param messageUid the unique message id
     * @param text the previous text
     * @param createdAt when the previous text was created
     * @param editHistoryType whether a contact or group message has been edited
     * @param localMessageId the local database message id
     *
     * @throws EditHistoryEntryCreateException if inserting the [DbEditHistoryEntry] in the database failed
     */
    @Throws(EditHistoryEntryCreateException::class)
    fun createEntry(
        messageUid: MessageUid,
        text: String,
        createdAt: Instant,
        editHistoryType: EditHistoryType,
        localMessageId: LocalMessageId,
    ) {
        synchronized(this) {
            try {
                val dbEditHistoryEntry = editHistoryDao.create(
                    dbEditHistoryEntry = DbEditHistoryEntry(
                        messageUid = messageUid,
                        messageId = localMessageId,
                        text = text,
                        createdAt = createdAt,
                    ),
                    editHistoryType = editHistoryType,
                )
                cache[messageUid]?.addEntry(dbEditHistoryEntry.toDataType())
            } catch (e: SQLiteException) {
                throw EditHistoryEntryCreateException(e)
            } catch (e: ModelDeletedException) {
                logger.error("Can't add entry to an already deleted model", e)
                throw EditHistoryEntryCreateException(e)
            }
        }
    }

    /**
     * Create multiple entries.
     *
     * @throws EditHistoryEntryCreateException if inserting the [DbEditHistoryEntry] in the database failed
     */
    @Throws(EditHistoryEntryCreateException::class)
    fun createEntries(
        contactMessagesHistoryEntries: Map<ContactMessageModel, List<MessageHistoryEntry>>,
        groupMessagesHistoryEntries: Map<GroupMessageModel, List<MessageHistoryEntry>>,
    ) {
        if (contactMessagesHistoryEntries.isEmpty() && groupMessagesHistoryEntries.isEmpty()) {
            return
        }
        val editHistoryEntriesContactMessages = contactMessagesHistoryEntries.flatMap { (messageModel, historyEntries) ->
            historyEntries.map { historyEntry -> messageModel.toDbEditHistoryEntry(historyEntry) }
        }
        val editHistoryEntriesGroupMessages = groupMessagesHistoryEntries.flatMap { (groupMessage, historyEntries) ->
            historyEntries.map { historyEntry -> groupMessage.toDbEditHistoryEntry(historyEntry) }
        }
        synchronized(this) {
            val dbEditHistoryEntries = try {
                editHistoryDao.createAll(
                    editHistoryEntriesContactMessages = editHistoryEntriesContactMessages,
                    editHistoryEntriesGroupMessages = editHistoryEntriesGroupMessages,
                )
            } catch (e: SQLiteException) {
                throw EditHistoryEntryCreateException(e)
            }
            for (dbEditHistoryEntry in dbEditHistoryEntries) {
                try {
                    cache[dbEditHistoryEntry.messageUid]?.addEntry(dbEditHistoryEntry.toDataType())
                } catch (e: ModelDeletedException) {
                    logger.error("Can't add entry to an already deleted model", e)
                }
            }
        }
    }

    @Synchronized
    fun deleteByMessageUid(messageUid: MessageUid) {
        logger.debug("Delete by message uid {}", messageUid)
        editHistoryDao.deleteAllByMessageUid(messageUid)
        try {
            cache[messageUid]?.delete()
        } catch (e: ModelDeletedException) {
            logger.error("Model is already deleted", e)
        }
        cache.remove(messageUid)
    }
}

private fun MessageModel.toDbEditHistoryEntry(historyEntry: MessageHistoryEntry) =
    DbEditHistoryEntry(
        messageUid = uid!!,
        messageId = id,
        text = historyEntry.body,
        createdAt = historyEntry.createdAt.toEpochMilliInstant(),
    )

class EditHistoryEntryCreateException(cause: Exception? = null) :
    ThreemaException("Failed to create an edit history entry in the db", cause)
