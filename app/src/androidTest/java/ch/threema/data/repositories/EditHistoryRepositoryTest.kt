package ch.threema.data.repositories

import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestTaskManager
import ch.threema.data.ModelCache
import ch.threema.data.datatypes.EditHistoryType
import ch.threema.data.datatypes.MessageHistoryEntry
import ch.threema.data.models.EditHistoryEntryData
import ch.threema.data.storage.EditHistoryDao
import ch.threema.data.storage.EditHistoryDaoImpl
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.models.MessageId
import ch.threema.storage.factories.ContactMessageModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.test.TestData
import ch.threema.test.TestDatabaseProvider
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EditHistoryRepositoryTest {
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var contactMessageModelFactory: ContactMessageModelFactory
    private lateinit var groupMessageModelFactory: GroupMessageModelFactory
    private lateinit var multiDeviceManager: TestMultiDeviceManager
    private lateinit var taskManager: TestTaskManager
    private lateinit var editHistoryRepository: EditHistoryRepository
    private lateinit var editHistoryDao: EditHistoryDao

    @BeforeTest
    fun before() {
        databaseProvider = TestDatabaseProvider()
        contactMessageModelFactory = ContactMessageModelFactory(databaseProvider)
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
        val contactMessage = ContactMessageModel().enrich()

        assertFailsWith<EditHistoryEntryCreateException> {
            editHistoryRepository.createEntry(
                messageUid = contactMessage.uid!!,
                text = contactMessage.body!!,
                createdAt = contactMessage.editedAt ?: contactMessage.createdAt!!,
                editHistoryType = EditHistoryType.CONTACT,
                localMessageId = contactMessage.id,
            )
        }

        contactMessageModelFactory.create(contactMessage)

        contactMessage.assertEditHistorySize(0)

        contactMessage.body = "Edited"

        editHistoryRepository.createEntry(
            messageUid = contactMessage.uid!!,
            text = contactMessage.body!!,
            createdAt = contactMessage.editedAt ?: contactMessage.createdAt!!,
            editHistoryType = EditHistoryType.CONTACT,
            localMessageId = contactMessage.id,
        )
        contactMessageModelFactory.update(contactMessage)

        contactMessage.assertEditHistorySize(1)

        contactMessageModelFactory.delete(contactMessage)

        contactMessage.assertEditHistorySize(0)
    }

    @Test
    fun testGroupMessageHistoryForeignKeyConstraint() {
        val groupMessage = GroupMessageModel().enrich()

        assertFailsWith<EditHistoryEntryCreateException> {
            editHistoryRepository.createEntry(
                messageUid = groupMessage.uid!!,
                text = groupMessage.body!!,
                createdAt = groupMessage.editedAt ?: groupMessage.createdAt!!,
                editHistoryType = EditHistoryType.GROUP,
                localMessageId = groupMessage.id,
            )
        }

        groupMessageModelFactory.create(groupMessage)

        groupMessage.assertEditHistorySize(0)

        groupMessage.body = "Edited"

        editHistoryRepository.createEntry(
            messageUid = groupMessage.uid!!,
            text = groupMessage.body!!,
            createdAt = groupMessage.editedAt ?: groupMessage.createdAt!!,
            editHistoryType = EditHistoryType.GROUP,
            localMessageId = groupMessage.id,
        )
        groupMessageModelFactory.update(groupMessage)

        groupMessage.assertEditHistorySize(1)

        groupMessageModelFactory.delete(groupMessage)

        groupMessage.assertEditHistorySize(0)
    }

    @Test
    fun createEntries_creates_all_entries() {
        // arrange
        val contactTextMessage1 = TestData.createContactTextMessage(
            id = 1,
            uid = TestData.UUIDs.UUID_1,
            messageId = MessageId(1L),
            identity = TestData.Identities.OTHER_1,
            isOutbox = true,
            text = "Message 1",
        )
        contactMessageModelFactory.create(contactTextMessage1)
        val contactTextMessage2 = TestData.createContactTextMessage(
            id = 2,
            uid = TestData.UUIDs.UUID_2,
            messageId = MessageId(2L),
            identity = TestData.Identities.OTHER_2,
            isOutbox = false,
            text = "Message 2",
        )
        contactMessageModelFactory.create(contactTextMessage2)
        val groupTextMessage1 = TestData.createGroupTextMessage(
            groupDatabaseId = 1,
            id = 1,
            uid = TestData.UUIDs.UUID_3,
            messageId = MessageId(1L),
            identity = null,
            isOutbox = true,
            text = "Group Message 1",
        )
        groupMessageModelFactory.create(groupTextMessage1)
        val groupTextMessage2 = TestData.createGroupTextMessage(
            groupDatabaseId = 2,
            id = 2,
            uid = TestData.UUIDs.UUID_4,
            messageId = MessageId(2L),
            identity = null,
            isOutbox = true,
            text = "Group Message 2",
        )
        groupMessageModelFactory.create(groupTextMessage2)

        // act
        editHistoryRepository.createEntries(
            contactMessagesHistoryEntries = mapOf(
                contactTextMessage1 to listOf(
                    MessageHistoryEntry(body = "Message 1", createdAt = 1u),
                    MessageHistoryEntry(body = "Message 1 - edit 1", createdAt = 11u),
                ),
                contactTextMessage2 to listOf(
                    MessageHistoryEntry(body = "Message 2", createdAt = 2u),
                    MessageHistoryEntry(body = "Message 2 - edit 1", createdAt = 21u),
                ),
            ),
            groupMessagesHistoryEntries = mapOf(
                groupTextMessage1 to listOf(
                    MessageHistoryEntry(body = "Group Message 1", createdAt = 1u),
                    MessageHistoryEntry(body = "Group Message 1 - edit 1", createdAt = 11u),
                ),
                groupTextMessage2 to listOf(
                    MessageHistoryEntry(body = "Group Message 2", createdAt = 2u),
                    MessageHistoryEntry(body = "Group Message 2 - edit 1", createdAt = 21u),
                ),
            ),
        )

        // assert
        assertEquals(
            expected = 2,
            actual = editHistoryDao.findAllByMessageUid(TestData.UUIDs.UUID_1).size,
        )
        val expectedEditHistoryEntryData1 = listOf(
            EditHistoryEntryData(
                uid = 2,
                messageUid = TestData.UUIDs.UUID_1,
                text = "Message 1 - edit 1",
                createdAt = Instant.ofEpochMilli(11L),
            ),
            EditHistoryEntryData(
                uid = 1,
                messageUid = TestData.UUIDs.UUID_1,
                text = "Message 1",
                createdAt = Instant.ofEpochMilli(1L),
            ),
        )
        assertEquals(
            expected = expectedEditHistoryEntryData1,
            actual = editHistoryRepository.getByMessageUid(TestData.UUIDs.UUID_1)?.data,
        )

        assertEquals(
            expected = 2,
            actual = editHistoryDao.findAllByMessageUid(TestData.UUIDs.UUID_2).size,
        )
        val expectedEditHistoryEntryData2 = listOf(
            EditHistoryEntryData(
                uid = 4,
                messageUid = TestData.UUIDs.UUID_2,
                text = "Message 2 - edit 1",
                createdAt = Instant.ofEpochMilli(21L),
            ),
            EditHistoryEntryData(
                uid = 3,
                messageUid = TestData.UUIDs.UUID_2,
                text = "Message 2",
                createdAt = Instant.ofEpochMilli(2L),
            ),
        )
        assertEquals(
            expected = expectedEditHistoryEntryData2,
            actual = editHistoryRepository.getByMessageUid(TestData.UUIDs.UUID_2)?.data,
        )

        assertEquals(
            expected = 2,
            actual = editHistoryDao.findAllByMessageUid(TestData.UUIDs.UUID_3).size,
        )
        val expectedEditHistoryEntryData3 = listOf(
            EditHistoryEntryData(
                uid = 2,
                messageUid = TestData.UUIDs.UUID_3,
                text = "Group Message 1 - edit 1",
                createdAt = Instant.ofEpochMilli(11L),
            ),
            EditHistoryEntryData(
                uid = 1,
                messageUid = TestData.UUIDs.UUID_3,
                text = "Group Message 1",
                createdAt = Instant.ofEpochMilli(1L),
            ),
        )
        assertEquals(
            expected = expectedEditHistoryEntryData3,
            actual = editHistoryRepository.getByMessageUid(TestData.UUIDs.UUID_3)?.data,
        )

        assertEquals(
            expected = 2,
            actual = editHistoryDao.findAllByMessageUid(TestData.UUIDs.UUID_4).size,
        )
        assertEquals(
            expected = listOf(
                EditHistoryEntryData(
                    uid = 4,
                    messageUid = TestData.UUIDs.UUID_4,
                    text = "Group Message 2 - edit 1",
                    createdAt = Instant.ofEpochMilli(21L),
                ),
                EditHistoryEntryData(
                    uid = 3,
                    messageUid = TestData.UUIDs.UUID_4,
                    text = "Group Message 2",
                    createdAt = Instant.ofEpochMilli(2L),
                ),
            ),
            actual = editHistoryRepository.getByMessageUid(TestData.UUIDs.UUID_4)?.data,
        )

        assertEquals(
            mapOf(
                TestData.UUIDs.UUID_1 to expectedEditHistoryEntryData1,
                TestData.UUIDs.UUID_2 to expectedEditHistoryEntryData2,
                TestData.UUIDs.UUID_3 to expectedEditHistoryEntryData3,
            ),
            editHistoryRepository.getByMessageUids(
                listOf(
                    TestData.UUIDs.UUID_1,
                    TestData.UUIDs.UUID_2,
                    TestData.UUIDs.UUID_3,
                ),
            )
                .mapValues { (_, editHistoryListModel) -> editHistoryListModel.data },
        )
    }

    /**
     * Assert that the expected amount of entries exists for this message.
     * Note that this queries the database directly since there might still be some entries cached.
     * For example if a message is deleted, the history entries will also be deleted due to the foreign
     * key constraints. The entries will still remain in the cache until the application is restarted.
     * This is not a problem, because the message history cannot be displayed when the message was deleted.
     */
    private fun MessageModel.assertEditHistorySize(expectedSize: Int) {
        val actualSize = editHistoryDao.findAllByMessageUid(uid!!).size
        assertEquals(expectedSize, actualSize)
    }

    private fun <T : MessageModel> T.enrich(text: String = "Text"): T = apply {
        messageId = MessageId.random()
        type = MessageType.TEXT
        uid = UUID.randomUUID().toString()
        body = text
        createdAt = Instant.ofEpochMilli(42)
    }
}
