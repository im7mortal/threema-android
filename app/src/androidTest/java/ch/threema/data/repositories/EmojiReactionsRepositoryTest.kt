package ch.threema.data.repositories

import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestTaskManager
import ch.threema.app.testutils.TestHelpers
import ch.threema.data.ModelCache
import ch.threema.data.ModelTypeCache
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.EmojiReactionsRepository.ReactionMessageIdentifier
import ch.threema.data.storage.EmojiReactionsDao
import ch.threema.data.storage.EmojiReactionsDaoImpl
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.Identity
import ch.threema.storage.factories.ContactMessageModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.test.TestData
import ch.threema.test.TestDatabaseProvider
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.TestTimeProvider
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class EmojiReactionsRepositoryTest {
    private lateinit var multiDeviceManager: TestMultiDeviceManager
    private lateinit var taskManager: TestTaskManager
    private lateinit var contactMessageModelFactory: ContactMessageModelFactory
    private lateinit var groupMessageModelFactory: GroupMessageModelFactory
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var emojiReactionsRepository: EmojiReactionsRepository
    private lateinit var emojiReactionDao: EmojiReactionsDao

    private val reactedAt = Instant.ofEpochMilli(123456789)

    @BeforeTest
    fun before() {
        databaseProvider = TestDatabaseProvider()
        contactMessageModelFactory = ContactMessageModelFactory(databaseProvider)
        groupMessageModelFactory = GroupMessageModelFactory(databaseProvider)
        multiDeviceManager = TestMultiDeviceManager()
        taskManager = TestTaskManager(UnusedTaskCodec())

        val cache = ModelCache()
        emojiReactionDao = EmojiReactionsDaoImpl(databaseProvider)
        emojiReactionsRepository = EmojiReactionsRepository(
            cache = cache.emojiReaction,
            emojiReactionDao = emojiReactionDao,
            identityProvider = TestIdentityProvider(Identity(TestHelpers.TEST_CONTACT.identity)),
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
            timeProvider = TestTimeProvider(),
        )
    }

    @Test
    fun testEmojiReactionForeignKeyConstraint() {
        val contactMessage = ContactMessageModel().enrich()

        assertFailsWith<EmojiReactionEntryCreateException> {
            emojiReactionsRepository.createEntry(
                targetMessage = contactMessage,
                senderIdentity = "ABCDEFGH",
                emojiSequence = "\uD83C\uDFC8",
                reactedAt = reactedAt,
            )
        }

        contactMessageModelFactory.create(contactMessage)

        contactMessage.assertEmojiReactionSize(0)

        contactMessage.body = "reacted"

        emojiReactionsRepository.createEntry(
            targetMessage = contactMessage,
            senderIdentity = "ABCDEFGH",
            emojiSequence = "⚽",
            reactedAt = reactedAt,
        )
        contactMessageModelFactory.update(contactMessage)

        contactMessage.assertEmojiReactionSize(1)

        contactMessageModelFactory.delete(contactMessage)

        contactMessage.assertEmojiReactionSize(0)
    }

    @Test
    fun testGroupEmojiReactionForeignKeyConstraint() {
        val groupMessage = GroupMessageModel().enrich()

        assertFailsWith<EmojiReactionEntryCreateException> {
            emojiReactionsRepository.createEntry(
                targetMessage = groupMessage,
                senderIdentity = "ABCDEFGH",
                emojiSequence = "⚾",
                reactedAt = reactedAt,
            )
        }

        groupMessageModelFactory.create(groupMessage)

        groupMessage.assertEmojiReactionSize(0)

        groupMessage.body = "Reacted"

        emojiReactionsRepository.createEntry(
            targetMessage = groupMessage,
            senderIdentity = "ABCDEFGH",
            emojiSequence = "⚽",
            reactedAt = reactedAt,
        )
        groupMessageModelFactory.update(groupMessage)

        groupMessage.assertEmojiReactionSize(1)

        groupMessageModelFactory.delete(groupMessage)

        groupMessage.assertEmojiReactionSize(0)
    }

    @Test
    fun testEmojiReactionUniqueness() {
        val message = ContactMessageModel().enrich()
        contactMessageModelFactory.create(message)

        message.assertEmojiReactionSize(0)
        message.body = "reacted"

        emojiReactionsRepository.createEntry(
            targetMessage = message,
            senderIdentity = "ABCDEFGH",
            emojiSequence = "⚽",
            reactedAt = reactedAt,
        )
        contactMessageModelFactory.update(message)

        message.assertEmojiReactionSize(1)

        assertFailsWith<EmojiReactionEntryCreateException> {
            emojiReactionsRepository.createEntry(
                targetMessage = message,
                senderIdentity = "ABCDEFGH",
                emojiSequence = "⚽",
                reactedAt = reactedAt,
            )
        }

        message.assertEmojiReactionSize(1)

        val reactions = emojiReactionsRepository.getReactionsByMessage(message)
        assertNotNull(reactions)

        val reaction = reactions.data!![0]
        assertEquals("⚽", reaction.emojiSequence)

        contactMessageModelFactory.delete(message)
    }

    @Test
    fun testContactAndGroupReactionsNotMixedUp() {
        val contactMessage = ContactMessageModel().enrich()
        val groupMessage = GroupMessageModel().enrich()

        contactMessageModelFactory.create(contactMessage)
        groupMessageModelFactory.create(groupMessage)

        assertEquals(1, contactMessage.id)
        assertEquals(1, groupMessage.id)

        contactMessage.assertEmojiReactionSize(0)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(
            targetMessage = contactMessage,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "⚾",
            reactedAt = reactedAt,
        )

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(
            targetMessage = groupMessage,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "⛵",
            reactedAt = reactedAt,
        )

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(1)

        val contactReaction = emojiReactionsRepository.getReactionsByMessage(contactMessage)!!.data!![0]
        val groupReaction = emojiReactionsRepository.getReactionsByMessage(groupMessage)!!.data!![0]

        assertEquals("⚾", contactReaction.emojiSequence)
        assertEquals("⛵", groupReaction.emojiSequence)
    }

    @Test
    fun testContactAndGroupReactionsNotMixedUpWhenRemoved() {
        val reactionSequence = "⛵"

        val contactMessage = ContactMessageModel().enrich()
        val groupMessage = GroupMessageModel().enrich()

        contactMessageModelFactory.create(contactMessage)
        groupMessageModelFactory.create(groupMessage)

        assertEquals(1, contactMessage.id)
        assertEquals(1, groupMessage.id)

        contactMessage.assertEmojiReactionSize(0)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(
            targetMessage = contactMessage,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = reactionSequence,
            reactedAt = reactedAt,
        )

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(0)

        emojiReactionsRepository.createEntry(
            targetMessage = groupMessage,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = reactionSequence,
            reactedAt = reactedAt,
        )

        contactMessage.assertEmojiReactionSize(1)
        groupMessage.assertEmojiReactionSize(1)

        emojiReactionsRepository.removeEntry(contactMessage, TestData.Identities.OTHER_1.value, reactionSequence)

        contactMessage.assertEmojiReactionSize(0)
        groupMessage.assertEmojiReactionSize(1)

        emojiReactionsRepository.removeEntry(groupMessage, TestData.Identities.OTHER_1.value, reactionSequence)

        contactMessage.assertEmojiReactionSize(0)
        groupMessage.assertEmojiReactionSize(0)
    }

    @Test
    fun testEmojiReactionsModelCaching() {
        val testEmojiCache = ModelTypeCache<ReactionMessageIdentifier, EmojiReactionsModel>()

        val contactMessage = ContactMessageModel().enrich()
        contactMessageModelFactory.create(contactMessage)

        // Test successful creation of reaction-message-identifier
        val reactionMessageIdentifier = ReactionMessageIdentifier.fromMessageModel(contactMessage)
        assertNotNull(reactionMessageIdentifier)

        // Test unsuccessful creation of reaction-message-identifier
        val reactionMessageIdentifierNull = ReactionMessageIdentifier.fromMessageModel(
            messageModel = DistributionListMessageModel(),
        )
        assertNull(reactionMessageIdentifierNull)

        // Test reading empty cache
        var cachedEntry: EmojiReactionsModel? = testEmojiCache.get(reactionMessageIdentifier)
        assertNull(cachedEntry)

        // Test reading empty cache but creating entity
        val emojiReactionData = EmojiReactionData(
            contactMessage.id,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "⛵",
            reactedAt = Instant.now(),
        )
        val emojiReactionsModel = EmojiReactionsModel(
            data = listOf(emojiReactionData),
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
        )
        cachedEntry = testEmojiCache.getOrCreate(reactionMessageIdentifier) { emojiReactionsModel }
        assertContentEquals(listOf(emojiReactionData), cachedEntry!!.data)

        // Test should read the cached value
        testEmojiCache.getOrCreate(reactionMessageIdentifier) {
            fail("Should not call this miss() function")
        }
        assertNotNull(testEmojiCache.get(reactionMessageIdentifier))

        // Test removing from cache
        val removedEmojiReactionsModel = testEmojiCache.remove(reactionMessageIdentifier)
        assertContentEquals(listOf(emojiReactionData), removedEmojiReactionsModel!!.data)
        assertNull(testEmojiCache.get(reactionMessageIdentifier))
    }

    @Test
    fun testCacheCollision() {
        // arrange
        val testEmojiCache = ModelTypeCache<ReactionMessageIdentifier, EmojiReactionsModel>()
        val contactMessageId = 1
        val groupMessageId = 1
        val reactionMessageIdentifierContact = ReactionMessageIdentifier(
            messageId = contactMessageId,
            messageType = ReactionMessageIdentifier.TargetMessageType.ONE_TO_ONE,
        )
        val reactionMessageIdentifierGroup = ReactionMessageIdentifier(
            messageId = groupMessageId,
            messageType = ReactionMessageIdentifier.TargetMessageType.GROUP,
        )
        // Add only the emoji reaction of the 1:1 message to the cache
        val emojiReactionDataForContactMessage = EmojiReactionData(
            messageId = contactMessageId,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "⛵",
            reactedAt = Instant.now(),
        )
        val emojiReactionsModelContact = EmojiReactionsModel(
            data = listOf(emojiReactionDataForContactMessage),
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
        )

        val cachedEntryContact =
            testEmojiCache.getOrCreate(reactionMessageIdentifierContact) { emojiReactionsModelContact }

        assertContentEquals(
            listOf(emojiReactionDataForContactMessage),
            cachedEntryContact!!.data,
        )
        assertNull(testEmojiCache.get(reactionMessageIdentifierGroup))

        testEmojiCache.remove(reactionMessageIdentifierGroup)
        assertNotNull(testEmojiCache.get(reactionMessageIdentifierContact))
    }

    @Test
    fun testGetReactionsForMultipleMessages() {
        // arrange
        val contactMessage1 = ContactMessageModel().enrich()
        val contactMessage2 = ContactMessageModel().enrich()
        val groupMessage1 = GroupMessageModel().enrich()
        val groupMessage2 = GroupMessageModel().enrich()
        val groupMessage3 = GroupMessageModel().enrich()

        contactMessageModelFactory.create(contactMessage1)
        contactMessageModelFactory.create(contactMessage2)
        groupMessageModelFactory.create(groupMessage1)
        groupMessageModelFactory.create(groupMessage2)
        groupMessageModelFactory.create(groupMessage3)

        emojiReactionsRepository.createEntry(
            targetMessage = contactMessage1,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "🍕",
            reactedAt = reactedAt,
        )
        emojiReactionsRepository.createEntry(
            targetMessage = contactMessage1,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "🍔",
            reactedAt = reactedAt,
        )
        emojiReactionsRepository.createEntry(
            targetMessage = contactMessage2,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "🍝",
            reactedAt = reactedAt,
        )
        emojiReactionsRepository.createEntry(
            targetMessage = groupMessage1,
            senderIdentity = TestData.Identities.OTHER_1.value,
            emojiSequence = "🥐",
            reactedAt = reactedAt,
        )
        emojiReactionsRepository.createEntry(
            targetMessage = groupMessage1,
            senderIdentity = TestData.Identities.OTHER_2.value,
            emojiSequence = "🍿",
            reactedAt = reactedAt,
        )
        emojiReactionsRepository.createEntry(
            targetMessage = groupMessage3,
            senderIdentity = TestData.Identities.OTHER_2.value,
            emojiSequence = "🍪",
            reactedAt = reactedAt,
        )

        // act
        val emojiReactionModelsByMessage = emojiReactionsRepository.getReactionsByMessages(
            listOf(contactMessage1, contactMessage2, groupMessage1, groupMessage2),
        )

        // assert
        assertEquals(
            mapOf(
                contactMessage1 to listOf("🍕", "🍔"),
                contactMessage2 to listOf("🍝"),
                groupMessage1 to listOf("🥐", "🍿"),
                groupMessage2 to emptyList(),
            ),
            emojiReactionModelsByMessage.mapValues { (_, reactionsModel) ->
                reactionsModel.data?.map { it.emojiSequence }
            },
        )
    }

    private fun MessageModel.assertEmojiReactionSize(expectedSize: Int) {
        val actualSize = emojiReactionDao.findAllByMessage(this).size

        assertEquals(expectedSize, actualSize)
    }

    private fun <T : MessageModel> T.enrich(text: String = "Text"): T {
        messageId = MessageId.random()
        type = MessageType.TEXT
        uid = UUID.randomUUID().toString()
        body = text
        return this
    }
}
