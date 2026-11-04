package ch.threema.storage.factories

import ch.threema.data.datatypes.DistributionListMessageIdentifier
import ch.threema.domain.models.MessageId
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.test.TestDatabaseProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistributionListMessageModelFactoryTest {

    private lateinit var distributionListMessageModelFactory: DistributionListMessageModelFactory

    @BeforeTest
    fun beforeTest() {
        distributionListMessageModelFactory = DistributionListMessageModelFactory(TestDatabaseProvider())
    }

    @Test
    fun anyExists_empty_input() {
        // act
        val anyExists = distributionListMessageModelFactory.anyExists(emptySet<DistributionListMessageIdentifier>())

        // assert
        assertFalse(anyExists)
    }

    @Test
    fun anyExists_empty_db() {
        // act
        val anyExists = distributionListMessageModelFactory.anyExists(
            setOf(
                DistributionListMessageIdentifier(
                    messageId = MessageId(1L),
                    distributionListId = 1L,
                ),
                DistributionListMessageIdentifier(
                    messageId = MessageId(2L),
                    distributionListId = 2L,
                ),
            ),
        )

        // assert
        assertFalse(anyExists)
    }

    @Test
    fun anyExists_none() {
        // arrange
        distributionListMessageModelFactory.create(
            listOf(
                createDistributionListMessageModel(
                    apiMessageId = 1L,
                    distributionListId = 1L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 2L,
                    distributionListId = 1L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 3L,
                    distributionListId = 2L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 4L,
                    distributionListId = 2L,
                ),
            ),
        )

        // act
        val anyExists = distributionListMessageModelFactory.anyExists(
            setOf(
                DistributionListMessageIdentifier(
                    messageId = MessageId(3L),
                    distributionListId = 1L,
                ),
                DistributionListMessageIdentifier(
                    messageId = MessageId(1L),
                    distributionListId = 2L,
                ),
                DistributionListMessageIdentifier(
                    messageId = MessageId(5L),
                    distributionListId = 5L,
                ),
            ),
        )

        // assert
        assertFalse(anyExists)
    }

    @Test
    fun anyExists_single() {
        // arrange
        distributionListMessageModelFactory.create(
            listOf(
                createDistributionListMessageModel(
                    apiMessageId = 1L,
                    distributionListId = 1L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 2L,
                    distributionListId = 1L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 3L,
                    distributionListId = 2L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 4L,
                    distributionListId = 2L,
                ),
            ),
        )

        // act
        val anyExists = distributionListMessageModelFactory.anyExists(
            setOf(
                DistributionListMessageIdentifier(
                    messageId = MessageId(3L),
                    distributionListId = 1L,
                ),
                DistributionListMessageIdentifier(
                    messageId = MessageId(1L),
                    distributionListId = 2L,
                ),
                DistributionListMessageIdentifier(
                    messageId = MessageId(2L),
                    distributionListId = 1L,
                ),
            ),
        )

        // assert
        assertTrue(anyExists)
    }

    @Test
    fun anyExists_multiple() {
        // arrange
        distributionListMessageModelFactory.create(
            listOf(
                createDistributionListMessageModel(
                    apiMessageId = 1L,
                    distributionListId = 1L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 2L,
                    distributionListId = 1L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 3L,
                    distributionListId = 2L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 4L,
                    distributionListId = 2L,
                ),
            ),
        )

        // act
        val anyExists = distributionListMessageModelFactory.anyExists(
            setOf(
                DistributionListMessageIdentifier(
                    messageId = MessageId(3L),
                    distributionListId = 1L,
                ),
                DistributionListMessageIdentifier(
                    messageId = MessageId(1L),
                    distributionListId = 2L,
                ),
                DistributionListMessageIdentifier(
                    messageId = MessageId(2L),
                    distributionListId = 1L,
                ),
                DistributionListMessageIdentifier(
                    messageId = MessageId(4L),
                    distributionListId = 2L,
                ),
            ),
        )

        // assert
        assertTrue(anyExists)
    }

    @Test
    fun anyExists_all() {
        // arrange
        distributionListMessageModelFactory.create(
            listOf(
                createDistributionListMessageModel(
                    apiMessageId = 1L,
                    distributionListId = 1L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 2L,
                    distributionListId = 1L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 3L,
                    distributionListId = 2L,
                ),
                createDistributionListMessageModel(
                    apiMessageId = 4L,
                    distributionListId = 2L,
                ),
            ),
        )

        // act
        val anyExists = distributionListMessageModelFactory.anyExists(
            setOf(
                DistributionListMessageIdentifier(
                    messageId = MessageId(2L),
                    distributionListId = 1L,
                ),
                DistributionListMessageIdentifier(
                    messageId = MessageId(3L),
                    distributionListId = 2L,
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
        fun createDistributionListMessageModel(
            apiMessageId: Long,
            messageUid: String = apiMessageId.toString(),
            distributionListId: Long,
            isOutbox: Boolean = true,
        ) = DistributionListMessageModel().apply {
            this.messageId = MessageId(apiMessageId)
            this.uid = messageUid
            this.distributionListId = distributionListId
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
