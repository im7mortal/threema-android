package ch.threema.storage.factories

import ch.threema.data.datatypes.GroupMessageIdentifier
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.test.TestDatabaseProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupMessageModelFactoryTest {

    private lateinit var groupMessageModelFactory: GroupMessageModelFactory

    @BeforeTest
    fun beforeTest() {
        groupMessageModelFactory = GroupMessageModelFactory(TestDatabaseProvider())
    }

    @Test
    fun anyExists_empty_input() {
        // act
        val anyExists = groupMessageModelFactory.anyExists(emptySet<GroupMessageIdentifier>())

        // assert
        assertFalse(anyExists)
    }

    @Test
    fun anyExists_empty_db() {
        // act
        val anyExists = groupMessageModelFactory.anyExists(
            setOf(
                GroupMessageIdentifier(
                    messageId = MessageId(1L),
                    groupDatabaseId = 1L,
                ),
                GroupMessageIdentifier(
                    messageId = MessageId(2L),
                    groupDatabaseId = 2L,
                ),
            ),
        )

        // assert
        assertFalse(anyExists)
    }

    @Test
    fun anyExists_none() {
        // arrange
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 1L,
                messageUid = "1",
                groupDatabaseId = 1L,
            ),
        )
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 2L,
                messageUid = "2",
                groupDatabaseId = 1L,
            ),
        )
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 3L,
                messageUid = "3",
                groupDatabaseId = 2L,
            ),
        )

        // act
        val anyExists = groupMessageModelFactory.anyExists(
            setOf(
                GroupMessageIdentifier(
                    messageId = MessageId(4L),
                    groupDatabaseId = 1L,
                ),
                GroupMessageIdentifier(
                    messageId = MessageId(2L),
                    groupDatabaseId = 2L,
                ),
            ),
        )

        // assert
        assertFalse(anyExists)
    }

    @Test
    fun anyExists_single() {
        // arrange
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 1L,
                messageUid = "1",
                groupDatabaseId = 1L,
            ),
        )
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 2L,
                messageUid = "2",
                groupDatabaseId = 1L,
            ),
        )
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 3L,
                messageUid = "3",
                groupDatabaseId = 2L,
            ),
        )

        // act
        val anyExists = groupMessageModelFactory.anyExists(
            setOf(
                GroupMessageIdentifier(
                    messageId = MessageId(2L),
                    groupDatabaseId = 1L,
                ),
                GroupMessageIdentifier(
                    messageId = MessageId(4L),
                    groupDatabaseId = 1L,
                ),
            ),
        )

        // assert
        assertTrue(anyExists)
    }

    @Test
    fun anyExists_multiple() {
        // arrange
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 1L,
                messageUid = "1",
                groupDatabaseId = 1L,
            ),
        )
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 2L,
                messageUid = "2",
                groupDatabaseId = 1L,
            ),
        )
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 3L,
                messageUid = "3",
                groupDatabaseId = 2L,
            ),
        )

        // act
        val anyExists = groupMessageModelFactory.anyExists(
            setOf(
                GroupMessageIdentifier(
                    messageId = MessageId(2L),
                    groupDatabaseId = 1L,
                ),
                GroupMessageIdentifier(
                    messageId = MessageId(3L),
                    groupDatabaseId = 2L,
                ),
                GroupMessageIdentifier(
                    messageId = MessageId(4L),
                    groupDatabaseId = 4L,
                ),
            ),
        )

        // assert
        assertTrue(anyExists)
    }

    @Test
    fun anyExists_all() {
        // arrange
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 1L,
                messageUid = "1",
                groupDatabaseId = 1L,
            ),
        )
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 2L,
                messageUid = "2",
                groupDatabaseId = 1L,
            ),
        )
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 3L,
                messageUid = "3",
                groupDatabaseId = 2L,
            ),
        )
        groupMessageModelFactory.create(
            createGroupMessageModel(
                apiMessageId = 3L,
                messageUid = "4",
                groupDatabaseId = 2L,
            ),
        )

        // act
        val anyExists = groupMessageModelFactory.anyExists(
            setOf(
                GroupMessageIdentifier(
                    messageId = MessageId(2L),
                    groupDatabaseId = 1L,
                ),
                GroupMessageIdentifier(
                    messageId = MessageId(3L),
                    groupDatabaseId = 2L,
                ),
            ),
        )

        // assert
        assertTrue(anyExists)
    }

    private companion object {

        /**
         * @param messageUid The message's uid (globally unique)
         */
        fun createGroupMessageModel(
            apiMessageId: Long,
            messageUid: String,
            groupDatabaseId: GroupDatabaseId,
            isOutbox: Boolean = true,
        ) = GroupMessageModel().apply {
            this.messageId = MessageId(apiMessageId)
            this.uid = messageUid
            this.groupId = groupDatabaseId.toInt()
            this.postedAt = utcDate(2026, 7, 28)
            this.createdAt = utcDate(2026, 7, 28)
            this.editedAt = utcDate(2026, 7, 29)
            this.isOutbox = isOutbox
            this.isSaved = true
            this.type = MessageType.TEXT
            this.body = "Message body"
        }
    }
}
