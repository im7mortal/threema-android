package ch.threema.data.storage

import androidx.sqlite.SQLiteException
import ch.threema.domain.models.MessageId
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.factories.ContactEditHistoryEntryModelFactory
import ch.threema.storage.factories.ContactMessageModelFactory
import ch.threema.storage.factories.GroupEditHistoryEntryModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.test.TestData
import ch.threema.test.TestDatabaseProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EditHistoryDaoImplTest {

    fun runDaoTest(
        test: (
            editHistoryDao: EditHistoryDao,
            databaseProvider: DatabaseProvider,
            contactMessageModelFactory: ContactMessageModelFactory,
            groupMessageModelFactory: GroupMessageModelFactory,
        ) -> Unit,
    ) {
        val databaseProvider = TestDatabaseProvider()
        test(
            EditHistoryDaoImpl(databaseProvider),
            databaseProvider,
            ContactMessageModelFactory(databaseProvider),
            GroupMessageModelFactory(databaseProvider),
        )
    }

    @Test
    fun createAll_returns_empty_on_empty_input() = runDaoTest { editHistoryDao, databaseProvider, _, _ ->
        // act
        val dbEditHistoryEntries = editHistoryDao.createAll(
            editHistoryEntriesContactMessages = emptyList(),
            editHistoryEntriesGroupMessages = emptyList(),
        )

        // assert
        assertEquals(
            expected = 0,
            actual = dbEditHistoryEntries.size,
        )
        val countContactEntries = databaseProvider.countAllContactMessageEditHistoryItems()
        assertEquals(
            expected = 0,
            actual = countContactEntries,
        )
        val countGroupEntries = databaseProvider.countAllGroupMessageEditHistoryItems()
        assertEquals(
            expected = 0,
            actual = countGroupEntries,
        )
    }

    @Test
    fun createAll_should_rollback_if_contact_message_not_exists() = runDaoTest { editHistoryDao, databaseProvider, contactMessageModelFactory, _ ->
        // arrange
        val outgoingTextMessage = TestData.createContactTextMessage(
            id = 1,
            uid = TestData.UUIDs.UUID_1,
            messageId = MessageId(1L),
            identity = TestData.Identities.OTHER_1,
            isOutbox = true,
            text = "Message",
        )
        contactMessageModelFactory.create(outgoingTextMessage)

        // act / assert
        assertFailsWith<SQLiteException> {
            editHistoryDao.createAll(
                editHistoryEntriesContactMessages = listOf(
                    DbEditHistoryEntry(
                        messageUid = outgoingTextMessage.uid!!,
                        messageId = outgoingTextMessage.id,
                        text = outgoingTextMessage.body + " - edit 1",
                        createdAt = Instant.ofEpochMilli(1),
                    ),
                    DbEditHistoryEntry(
                        messageUid = TestData.UUIDs.UUID_2,
                        messageId = 2,
                        text = "Message does not exist",
                        createdAt = Instant.ofEpochMilli(2),
                    ),
                ),
                editHistoryEntriesGroupMessages = emptyList(),
            )
        }
        assertEquals(
            expected = 0,
            actual = databaseProvider.countAllContactMessageEditHistoryItems(),
        )
    }

    @Test
    fun createAll_should_rollback_if_group_message_not_exists() = runDaoTest { editHistoryDao, databaseProvider, _, groupMessageModelFactory ->
        // arrange
        val outgoingGroupTextMessage = TestData.createGroupTextMessage(
            groupDatabaseId = 1L,
            id = 1,
            uid = TestData.UUIDs.UUID_1,
            messageId = MessageId(1L),
            identity = TestData.Identities.OTHER_1,
            isOutbox = true,
            text = "Group message",
        )
        groupMessageModelFactory.create(outgoingGroupTextMessage)

        // act / assert
        assertFailsWith<SQLiteException> {
            editHistoryDao.createAll(
                editHistoryEntriesContactMessages = emptyList(),
                editHistoryEntriesGroupMessages = listOf(
                    DbEditHistoryEntry(
                        messageUid = outgoingGroupTextMessage.uid!!,
                        messageId = outgoingGroupTextMessage.id,
                        text = outgoingGroupTextMessage.body + " - edit 1",
                        createdAt = Instant.ofEpochMilli(1),
                    ),
                    DbEditHistoryEntry(
                        messageUid = TestData.UUIDs.UUID_2,
                        messageId = 2,
                        text = "Message does not exist",
                        createdAt = Instant.ofEpochMilli(2),
                    ),
                ),
            )
        }
        assertEquals(
            expected = 0,
            actual = databaseProvider.countAllGroupMessageEditHistoryItems(),
        )
    }

    @Test
    fun createAll_should_create_all() = runDaoTest { editHistoryDao, databaseProvider, contactMessageModelFactory, groupMessageModelFactory ->
        // arrange
        val outgoingContactTextMessage1 = TestData.createContactTextMessage(
            id = 1,
            uid = TestData.UUIDs.UUID_1,
            messageId = MessageId(1L),
            identity = TestData.Identities.OTHER_1,
            isOutbox = true,
            text = "Message 1",
        )
        contactMessageModelFactory.create(outgoingContactTextMessage1)
        val outgoingContactTextMessage2 = TestData.createContactTextMessage(
            id = 2,
            uid = TestData.UUIDs.UUID_2,
            messageId = MessageId(2L),
            identity = TestData.Identities.OTHER_2,
            isOutbox = false,
            text = "Message 2",
        )
        contactMessageModelFactory.create(outgoingContactTextMessage2)
        val outgoingGroupTextMessage1 = TestData.createGroupTextMessage(
            groupDatabaseId = 1L,
            id = 1,
            uid = TestData.UUIDs.UUID_3,
            messageId = MessageId(1L),
            identity = null,
            isOutbox = true,
            text = "Group message 1",
        )
        groupMessageModelFactory.create(outgoingGroupTextMessage1)
        val outgoingGroupTextMessage2 = TestData.createGroupTextMessage(
            groupDatabaseId = 2L,
            id = 2,
            uid = TestData.UUIDs.UUID_4,
            messageId = MessageId(2L),
            identity = TestData.Identities.OTHER_1,
            isOutbox = false,
            text = "Group message 2",
        )
        groupMessageModelFactory.create(outgoingGroupTextMessage2)

        // act
        val editHistoryEntries = editHistoryDao.createAll(
            editHistoryEntriesContactMessages = listOf(
                DbEditHistoryEntry(
                    messageUid = outgoingContactTextMessage1.uid!!,
                    messageId = outgoingContactTextMessage1.id,
                    text = outgoingContactTextMessage1.body + " - edit 1",
                    createdAt = Instant.ofEpochMilli(1),
                ),
                DbEditHistoryEntry(
                    messageUid = outgoingContactTextMessage2.uid!!,
                    messageId = outgoingContactTextMessage2.id,
                    text = outgoingContactTextMessage2.body + " - edit 1",
                    createdAt = Instant.ofEpochMilli(2),
                ),
                DbEditHistoryEntry(
                    messageUid = outgoingContactTextMessage2.uid!!,
                    messageId = outgoingContactTextMessage2.id,
                    text = outgoingContactTextMessage2.body + " - edit 2",
                    createdAt = Instant.ofEpochMilli(3),
                ),
                DbEditHistoryEntry(
                    messageUid = outgoingContactTextMessage2.uid!!,
                    messageId = outgoingContactTextMessage2.id,
                    text = outgoingContactTextMessage2.body + " - edit 3",
                    createdAt = Instant.ofEpochMilli(4),
                ),
            ),
            editHistoryEntriesGroupMessages = listOf(
                DbEditHistoryEntry(
                    messageUid = outgoingGroupTextMessage1.uid!!,
                    messageId = outgoingGroupTextMessage1.id,
                    text = outgoingGroupTextMessage1.body + " - edit 1",
                    createdAt = Instant.ofEpochMilli(5),
                ),
                DbEditHistoryEntry(
                    messageUid = outgoingGroupTextMessage1.uid!!,
                    messageId = outgoingGroupTextMessage1.id,
                    text = outgoingGroupTextMessage1.body + " - edit 2",
                    createdAt = Instant.ofEpochMilli(6),
                ),
                DbEditHistoryEntry(
                    messageUid = outgoingGroupTextMessage1.uid!!,
                    messageId = outgoingGroupTextMessage1.id,
                    text = outgoingGroupTextMessage1.body + " - edit 3",
                    createdAt = Instant.ofEpochMilli(7),
                ),
                DbEditHistoryEntry(
                    messageUid = outgoingGroupTextMessage2.uid!!,
                    messageId = outgoingGroupTextMessage2.id,
                    text = outgoingGroupTextMessage2.body + " - edit 1",
                    createdAt = Instant.ofEpochMilli(8),
                ),
            ),
        )

        // assert
        assertEquals(8, editHistoryEntries.size)
        assertEquals(
            expected = 4,
            actual = databaseProvider.countAllContactMessageEditHistoryItems(),
        )
        assertEquals(
            expected = 4,
            actual = databaseProvider.countAllGroupMessageEditHistoryItems(),
        )
        assertEquals(
            mapOf(
                outgoingContactTextMessage1.uid!! to editHistoryEntries.filter { it.messageUid == outgoingContactTextMessage1.uid!! }.reversed(),
                outgoingContactTextMessage2.uid!! to editHistoryEntries.filter { it.messageUid == outgoingContactTextMessage2.uid!! }.reversed(),
                outgoingGroupTextMessage1.uid!! to editHistoryEntries.filter { it.messageUid == outgoingGroupTextMessage1.uid!! }.reversed(),
                outgoingGroupTextMessage2.uid!! to editHistoryEntries.filter { it.messageUid == outgoingGroupTextMessage2.uid!! }.reversed(),
            ),
            editHistoryDao.findAllByMessageUids(
                listOf(
                    outgoingContactTextMessage1.uid!!,
                    outgoingContactTextMessage2.uid!!,
                    outgoingGroupTextMessage1.uid!!,
                    outgoingGroupTextMessage2.uid!!,
                ),
            ),
        )
    }

    private fun DatabaseProvider.countAllContactMessageEditHistoryItems(): Long =
        readableDatabase
            .query("SELECT COUNT(*) FROM ${ContactEditHistoryEntryModelFactory.TABLE}")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }

    private fun DatabaseProvider.countAllGroupMessageEditHistoryItems(): Long =
        readableDatabase
            .query("SELECT COUNT(*) FROM ${GroupEditHistoryEntryModelFactory.TABLE}")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
}
