package ch.threema.data.storage

import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.domain.models.MessageId
import ch.threema.storage.factories.ContactMessageModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.test.TestData
import ch.threema.test.TestDatabaseProvider
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmojiReactionsDaoImplTest {

    @Test
    fun findAllByMessages() {
        val emojiReactionsDaoImpl = EmojiReactionsDaoImpl(
            TestDatabaseProvider(enforceForeignKeyConstraints = false),
        )
        val reaction1 = DbEmojiReaction(
            messageId = 1,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "👾",
            reactedAt = Instant.ofEpochMilli(1000),
            readAt = null,
        )
        val reaction2 = DbEmojiReaction(
            messageId = 2,
            senderIdentity = TestData.Identities.OTHER_2.value,
            emojiSequence = "🦊",
            reactedAt = Instant.ofEpochMilli(9000),
            readAt = null,
        )
        val reaction4 = DbEmojiReaction(
            messageId = 4,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "🎁",
            reactedAt = Instant.ofEpochMilli(3000),
            readAt = null,
        )
        val reaction5 = DbEmojiReaction(
            messageId = 5,
            senderIdentity = TestData.Identities.OTHER_2.value,
            emojiSequence = "🍒",
            reactedAt = Instant.ofEpochMilli(4000),
            readAt = null,
        )
        val reaction6 = DbEmojiReaction(
            messageId = 6,
            senderIdentity = TestData.Identities.OTHER_4.value,
            emojiSequence = "🦖",
            reactedAt = Instant.ofEpochMilli(2000),
            readAt = null,
        )
        with(emojiReactionsDaoImpl) {
            create(reaction1, mockk<GroupMessageModel> { every { id } returns 1 })
            create(reaction2, mockk<GroupMessageModel> { every { id } returns 2 })
            create(reaction4, mockk<ContactMessageModel> { every { id } returns 4 })
            create(reaction5, mockk<GroupMessageModel> { every { id } returns 5 })
            create(reaction6, mockk<GroupMessageModel> { every { id } returns 6 })
        }

        val reactions = emojiReactionsDaoImpl.findAllByMessages(
            listOf(
                mockk<GroupMessageModel> { every { id } returns 1 },
                mockk<GroupMessageModel> { every { id } returns 2 },
                mockk<DistributionListMessageModel> { every { id } returns 3 },
                mockk<ContactMessageModel> { every { id } returns 4 },
                mockk<GroupMessageModel> { every { id } returns 5 },
                mockk<ContactMessageModel> { every { id } returns 6 },
            ),
        )

        // Expect all reactions sorted by reactedAt DESC
        assertEquals(
            listOf(
                reaction2,
                reaction5,
                reaction4,
                reaction1,
            ),
            reactions,
        )
    }

    @Test
    fun markUnreadReactionsAsRead() {
        val databaseProvider = TestDatabaseProvider(enforceForeignKeyConstraints = false)
        val emojiReactionsDaoImpl = EmojiReactionsDaoImpl(databaseProvider)
        val contactMessage = ContactMessageModel().apply {
            messageId = MessageId.random()
            type = MessageType.TEXT
            uid = UUID.randomUUID().toString()
            body = "text"
            identity = TestData.Identities.OTHER_1.value
        }
        val groupMessage = GroupMessageModel().apply {
            messageId = MessageId.random()
            type = MessageType.TEXT
            uid = UUID.randomUUID().toString()
            body = "text"
            groupId = 1
        }
        ContactMessageModelFactory(databaseProvider).create(contactMessage)
        GroupMessageModelFactory(databaseProvider).create(groupMessage)

        val contactReactionUnread = DbEmojiReaction(
            messageId = contactMessage.id,
            senderIdentity = TestData.Identities.OTHER_2.value,
            emojiSequence = "👍",
            reactedAt = Instant.ofEpochMilli(1000),
            readAt = null,
        )
        val groupReactionUnread = DbEmojiReaction(
            messageId = groupMessage.id,
            senderIdentity = TestData.Identities.OTHER_2.value,
            emojiSequence = "👍",
            reactedAt = Instant.ofEpochMilli(1000),
            readAt = null,
        )
        with(emojiReactionsDaoImpl) {
            create(contactReactionUnread, contactMessage)
            create(groupReactionUnread, groupMessage)

            assertNull(findAllByMessage(contactMessage).single().readAt)
            assertNull(findAllByMessage(groupMessage).single().readAt)

            val readAt = Instant.ofEpochMilli(2000)
            assertEquals(
                expected = setOf(contactMessage.id),
                actual = markUnreadReactionsAsReadForContactConversation(
                    contactConversationId = ContactConversationId(TestData.Identities.OTHER_1.value),
                    readAt,
                ),
            )
            assertEquals(
                expected = setOf(groupMessage.id),
                actual = markUnreadReactionsAsReadForGroupConversation(
                    GroupConversationId(groupDatabaseId = 1L),
                    readAt,
                ),
            )

            // all reactions are already read - nothing to do
            assertEquals(
                expected = emptySet(),
                actual = markUnreadReactionsAsReadForContactConversation(
                    ContactConversationId(TestData.Identities.OTHER_1.value),
                    readAt,
                ),
            )
            assertEquals(
                expected = emptySet(),
                actual = markUnreadReactionsAsReadForGroupConversation(
                    GroupConversationId(1L),
                    readAt,
                ),
            )

            assertEquals(readAt, findAllByMessage(contactMessage).single().readAt)
            assertEquals(readAt, findAllByMessage(groupMessage).single().readAt)
        }
    }
}
