package ch.threema.data.repositories

import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestTaskManager
import ch.threema.data.ModelCache
import ch.threema.data.storage.EditHistoryDao
import ch.threema.data.storage.EditHistoryDaoImpl
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.models.MessageId
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.factories.MessageModelFactory
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.group.GroupMessageModel
import ch.threema.test.TestDatabaseProvider
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EditHistoryRepositoryTest {
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var messageModelFactory: MessageModelFactory
    private lateinit var groupMessageModelFactory: GroupMessageModelFactory
    private lateinit var multiDeviceManager: TestMultiDeviceManager
    private lateinit var taskManager: TestTaskManager
    private lateinit var editHistoryRepository: EditHistoryRepository
    private lateinit var editHistoryDao: EditHistoryDao

    @BeforeTest
    fun before() {
        databaseProvider = TestDatabaseProvider()
        messageModelFactory = MessageModelFactory(databaseProvider)
        groupMessageModelFactory = GroupMessageModelFactory(databaseProvider)
        multiDeviceManager = TestMultiDeviceManager()
        taskManager = TestTaskManager(UnusedTaskCodec())

        val cache = ModelCache()
        editHistoryDao = EditHistoryDaoImpl(databaseProvider)
        editHistoryRepository = EditHistoryRepository(
            cache = cache.editHistory,
            editHistoryDao = editHistoryDao,
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
        )
    }

    @Test
    fun testContactMessageHistoryForeignKeyConstraint() {
        val contactMessage = MessageModel().enrich()

        assertFailsWith<EditHistoryEntryCreateException> {
            editHistoryRepository.createEntry(contactMessage)
        }

        messageModelFactory.create(contactMessage)

        contactMessage.assertEditHistorySize(0)

        contactMessage.body = "Edited"

        editHistoryRepository.createEntry(contactMessage)
        messageModelFactory.update(contactMessage)

        contactMessage.assertEditHistorySize(1)

        messageModelFactory.delete(contactMessage)

        contactMessage.assertEditHistorySize(0)
    }

    @Test
    fun testGroupMessageHistoryForeignKeyConstraint() {
        val groupMessage = GroupMessageModel().enrich()

        assertFailsWith<EditHistoryEntryCreateException> {
            editHistoryRepository.createEntry(groupMessage)
        }

        groupMessageModelFactory.create(groupMessage)

        groupMessage.assertEditHistorySize(0)

        groupMessage.body = "Edited"

        editHistoryRepository.createEntry(groupMessage)
        groupMessageModelFactory.update(groupMessage)

        groupMessage.assertEditHistorySize(1)

        groupMessageModelFactory.delete(groupMessage)

        groupMessage.assertEditHistorySize(0)
    }

    /**
     * Assert that the expected amount of entries exists for this message.
     * Note that this queries the database directly since there might still be some entries cached.
     * For example if a message is deleted, the history entries will also be deleted due to the foreign
     * key constraints. The will still remain in the cache until the application is restarted.
     * This is not a problem, because the message history cannot be displayed when the message was deleted.
     */
    private fun AbstractMessageModel.assertEditHistorySize(expectedSize: Int) {
        val actualSize = editHistoryDao.findAllByMessageUid(uid!!).size

        assertEquals(expectedSize, actualSize)
    }

    private fun <T : AbstractMessageModel> T.enrich(text: String = "Text"): T = apply {
        messageId = MessageId.random()
        type = MessageType.TEXT
        uid = UUID.randomUUID().toString()
        body = text
    }
}
