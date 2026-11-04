package ch.threema.app.libthreema.helper

import ch.threema.app.libthreema.conversion.MessageModelConverter
import ch.threema.data.models.EditHistoryEntryData
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.EditHistoryRepository
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.domain.models.MessageId
import ch.threema.libthreema.EmojiReaction
import ch.threema.libthreema.MessageReaction
import ch.threema.libthreema.MessageState as LibthreemaMessageState
import ch.threema.libthreema.MessageStateBody
import ch.threema.libthreema.TextMessageState
import ch.threema.storage.factories.ContactMessageModelFactory
import ch.threema.storage.factories.DistributionListMessageModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.test.TestIdentityProvider
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import testdata.TestData

class MessageStateDatabaseReaderTest {

    @Test
    fun `reading messages succeeds when every batch is full`() {
        testReadingMessages(
            count = 5,
            batchSize = 5,
        )
        testReadingMessages(
            count = 10,
            batchSize = 5,
        )
        testReadingMessages(
            count = 15,
            batchSize = 5,
        )
    }

    @Test
    fun `reading messages succeeds with last batch not completely filled`() {
        testReadingMessages(
            count = 4,
            batchSize = 5,
        )
        testReadingMessages(
            count = 9,
            batchSize = 5,
        )
        testReadingMessages(
            count = 14,
            batchSize = 5,
        )
    }

    @Test
    fun `reading messages succeeds when no messages are available`() {
        testReadingMessages(
            count = 0,
            batchSize = 5,
        )
    }

    @Test
    fun `reading messages succeeds when batch size is one`() {
        testReadingMessages(
            count = 0,
            batchSize = 1,
        )
        testReadingMessages(
            count = 10,
            batchSize = 1,
        )
    }

    @Test
    fun `emoji conversion`() {
        // Arrange
        val messageModel = ContactMessageModel().apply {
            id = 123
            type = MessageType.TEXT
            identity = TestData.Identities.ME.value
            isOutbox = true
            messageId = MessageId.random()
            uid = UUID.randomUUID().toString()
        }
        val messageStateDatabaseReader = MessageStateDatabaseReader(
            contactMessageModelFactory = mockk {
                every { loadMessageModelsIntoList(any(), any(), any()) } answers {
                    val list = firstArg<MutableList<ContactMessageModel>>()
                    list.add(messageModel)
                }
            },
            groupMessageModelFactory = mockk(),
            distributionListMessageModelFactory = mockk(),
            emojiReactionsRepository = mockk {
                every { getReactionsByMessages<MessageModel>(any()) } returns mapOf(
                    messageModel to EmojiReactionsModel(
                        data = listOf(
                            EmojiReactionData(
                                messageId = 42,
                                senderIdentity = TestData.Identities.OTHER_1.value,
                                emojiSequence = "\uD83E\uDD96",
                                reactedAt = Instant.ofEpochMilli(1000),
                            ),
                        ),
                        multiDeviceManager = mockk(),
                        taskManager = mockk(),
                    ),
                )
            },
            editHistoryRepository = mockk {
                every { getByMessageUids(any()) } returns emptyMap()
            },
            messageModelConverter = MessageModelConverter(
                groupModelRepository = mockk(),
                pollModelFactory = mockk(),
                identityProvider = TestIdentityProvider(identity = TestData.Identities.ME),
                textMessageBodyConverter = mockk {
                    every { convertToLibthreemaTextMessageState(any(), any()) } returns mockk()
                },
                locationMessageBodyConverter = mockk(),
            ),
            batchSizeProvider = BatchSizeProvider(),
        )

        // Act
        val messageStates = messageStateDatabaseReader.readContactMessageBatch(batchIndex = 0UL)

        // Assert
        assertEquals(
            listOf(
                MessageReaction(
                    createdAt = 1000UL,
                    creatorIdentity = TestData.Identities.OTHER_1.value,
                    emoji = EmojiReaction("\uD83E\uDD96"),
                ),
            ),
            messageStates.single().reactions,
        )
    }

    @Test
    fun `edit history conversion`() {
        // Arrange
        val messageModel = ContactMessageModel().apply {
            id = 123
            type = MessageType.TEXT
            identity = TestData.Identities.ME.value
            isOutbox = true
            messageId = MessageId.random()
            uid = UUID.randomUUID().toString()
        }
        val messageStateMock = mockk<TextMessageState>()
        val editHistoryEntryDataListMock = mockk<List<EditHistoryEntryData>>()
        val messageStateDatabaseReader = MessageStateDatabaseReader(
            contactMessageModelFactory = mockk {
                every { loadMessageModelsIntoList(any(), any(), any()) } answers {
                    val list = firstArg<MutableList<ContactMessageModel>>()
                    list.add(messageModel)
                }
            },
            groupMessageModelFactory = mockk(),
            distributionListMessageModelFactory = mockk(),
            emojiReactionsRepository = createEmojiReactionsRepositoryMock(),
            editHistoryRepository = mockk {
                every { getByMessageUids(listOf(messageModel.uid!!)) } returns mapOf(
                    messageModel.uid!! to mockk {
                        every { data } returns editHistoryEntryDataListMock
                    },
                )
            },
            messageModelConverter = MessageModelConverter(
                groupModelRepository = mockk(),
                pollModelFactory = mockk(),
                identityProvider = TestIdentityProvider(identity = TestData.Identities.ME),
                textMessageBodyConverter = mockk {
                    every { convertToLibthreemaTextMessageState(any(), editHistoryEntryDataListMock) } returns messageStateMock
                },
                locationMessageBodyConverter = mockk(),
            ),
            batchSizeProvider = BatchSizeProvider(),
        )

        // Act
        val messageStates = messageStateDatabaseReader.readContactMessageBatch(batchIndex = 0UL)

        // Assert
        val body = messageStates.single().body
        assertIs<MessageStateBody.Text>(body)
        assertEquals(messageStateMock, body.v1)
    }

    private fun testReadingMessages(count: Int, batchSize: Int) {
        // Arrange
        val messageModels = createMessageModels(count = count)
        val contactMessageModelFactoryMock = createContactMessageModelFactoryMock(messageModels = messageModels)

        val groupMessageModels = createGroupMessageModels(count = count)
        val groupMessageModelFactoryMock = createGroupMessageModelFactoryMock(groupMessageModels = groupMessageModels)

        val distributionListMessageModels = createDistributionListMessageModels(count = count)
        val distributionListMessageModelFactoryMock =
            createDistributionListMessageModelFactoryMock(distributionListMessageModels = distributionListMessageModels)

        val messageStateDatabaseReader = MessageStateDatabaseReader(
            contactMessageModelFactory = contactMessageModelFactoryMock,
            groupMessageModelFactory = groupMessageModelFactoryMock,
            distributionListMessageModelFactory = distributionListMessageModelFactoryMock,
            emojiReactionsRepository = createEmojiReactionsRepositoryMock(),
            editHistoryRepository = createEditHistoryRepositoryMock(),
            messageModelConverter = createMessageModelConverterMock(),
            batchSizeProvider = BatchSizeProvider(
                contactMessageBatchSize = batchSize,
                groupMessageBatchSize = batchSize,
                distributionListMessageBatchSize = batchSize,
            ),
        )

        // Act
        val messageStates = generateSequence(0u) { it + 1u }
            .map { messageStateDatabaseReader.readContactMessageBatch(it.toULong()) }
            .takeWhile { messageStates -> messageStates.isNotEmpty() }
            .flatten()
            .toList()

        val groupMessageStates = generateSequence(0u) { it + 1u }
            .map { messageStateDatabaseReader.readGroupMessageBatch(it.toULong()) }
            .takeWhile { messageStates -> messageStates.isNotEmpty() }
            .flatten()
            .toList()

        val distributionListMessageStates = generateSequence(0u) { it + 1u }
            .map { messageStateDatabaseReader.readDistributionListMessage(it.toULong()) }
            .takeWhile { messageStates -> messageStates.isNotEmpty() }
            .flatten()
            .toList()

        // Assert
        val expectedMessageIds = (0 until count).map(Int::toLong)

        assertContentEquals(
            expected = expectedMessageIds,
            actual = messageStates.toMessageIdLongs(),
        )
        assertContentEquals(
            expected = expectedMessageIds,
            actual = groupMessageStates.toMessageIdLongs(),
        )
        assertContentEquals(
            expected = expectedMessageIds,
            actual = distributionListMessageStates.toMessageIdLongs(),
        )
    }

    private fun List<LibthreemaMessageState>.toMessageIdLongs() = map { messageState ->
        messageState.messageId.toLong()
    }

    private class BatchReadableList<T>(private val values: List<T>) {
        fun readBatch(count: Int, offset: Int): List<T> {
            require(offset >= 0)
            require(count >= 0)

            if (offset >= values.size || count == 0) {
                return emptyList()
            }

            return values.subList(
                fromIndex = offset,
                toIndex = (offset + count).coerceAtMost(values.size),
            )
        }
    }

    private fun createContactMessageModelFactoryMock(messageModels: List<ContactMessageModel>): ContactMessageModelFactory = mockk {
        val batchReadableList = BatchReadableList(values = messageModels)
        every { loadMessageModelsIntoList(any(), any(), any()) } answers {
            val messageModels = firstArg<MutableList<ContactMessageModel>>()
            val count = secondArg<Int>()
            val offset = thirdArg<Int>()

            batchReadableList.readBatch(
                count = count,
                offset = offset,
            ).forEach { messageModel ->
                messageModels.add(messageModel)
            }
        }
    }

    private fun createGroupMessageModelFactoryMock(groupMessageModels: List<GroupMessageModel>): GroupMessageModelFactory = mockk {
        val batchReadableList = BatchReadableList(values = groupMessageModels)
        every { loadMessageModelsIntoList(any(), any(), any()) } answers {
            val messageModels = firstArg<MutableList<GroupMessageModel>>()
            val count = secondArg<Int>()
            val offset = thirdArg<Int>()

            batchReadableList.readBatch(
                count = count,
                offset = offset,
            ).forEach { messageModel ->
                messageModels.add(messageModel)
            }
        }
    }

    private fun createDistributionListMessageModelFactoryMock(
        distributionListMessageModels: List<DistributionListMessageModel>,
    ): DistributionListMessageModelFactory =
        mockk {
            val batchReadableList = BatchReadableList(values = distributionListMessageModels)
            every { loadMessageModelsIntoList(any(), any(), any()) } answers {
                val messageModels = firstArg<MutableList<DistributionListMessageModel>>()
                val count = secondArg<Int>()
                val offset = thirdArg<Int>()

                batchReadableList.readBatch(
                    count = count,
                    offset = offset,
                ).forEach { messageModel ->
                    messageModels.add(messageModel)
                }
            }
        }

    private fun createEmojiReactionsRepositoryMock(): EmojiReactionsRepository =
        mockk {
            every { getReactionsByMessages<MessageModel>(any()) } returns emptyMap()
        }

    private fun createEditHistoryRepositoryMock(): EditHistoryRepository =
        mockk {
            every { getByMessageUids(any()) } returns emptyMap()
        }

    private fun createMessageModelConverterMock(): MessageModelConverter = mockk {
        every { convertToLibthreemaMessageState(any(), any(), any()) } answers {
            val messageModel = firstArg<MessageModel>()
            mockk {
                every { messageId } returns messageModel.messageId!!.messageIdLong.toULong()
            }
        }
    }

    private fun createMessageModels(count: Int): List<ContactMessageModel> = (0 until count).map { index ->
        ContactMessageModel().apply {
            messageId = MessageId(messageIdLong = index.toLong())
        }
    }

    private fun createGroupMessageModels(count: Int): List<GroupMessageModel> = (0 until count).map { index ->
        GroupMessageModel().apply {
            messageId = MessageId(messageIdLong = index.toLong())
        }
    }

    private fun createDistributionListMessageModels(count: Int): List<DistributionListMessageModel> = (0 until count).map { index ->
        DistributionListMessageModel().apply {
            messageId = MessageId(messageIdLong = index.toLong())
        }
    }
}
