package ch.threema.storage.factories

import ch.threema.data.datatypes.ContactMessageIdentifier
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.test.TestData.Identities
import ch.threema.test.TestDatabaseProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactMessageModelFactoryTest {

    private lateinit var contactMessageModelFactory: ContactMessageModelFactory

    @BeforeTest
    fun beforeTest() {
        contactMessageModelFactory = ContactMessageModelFactory(TestDatabaseProvider())
    }

    @Test
    fun anyExists_empty_input() {
        // act
        val anyExists = contactMessageModelFactory.anyExists(emptySet<ContactMessageIdentifier>())

        // assert
        assertFalse(anyExists)
    }

    @Test
    fun anyExists_empty_db() {
        // act
        val anyExists = contactMessageModelFactory.anyExists(
            setOf(
                ContactMessageIdentifier(
                    messageId = MessageId(1L),
                    identity = Identities.OTHER_1.value,
                ),
                ContactMessageIdentifier(
                    messageId = MessageId(2L),
                    identity = Identities.OTHER_2.value,
                ),
            ),
        )

        // assert
        assertFalse(anyExists)
    }

    @Test
    fun anyExists_none() {
        // arrange
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 1L,
                messageUid = "1",
                identity = Identities.OTHER_1,
            ),
        )
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 2L,
                messageUid = "2",
                identity = Identities.OTHER_2,
            ),
        )
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 3L,
                messageUid = "3",
                identity = Identities.OTHER_3,
            ),
        )

        // act
        val anyExists = contactMessageModelFactory.anyExists(
            setOf(
                ContactMessageIdentifier(
                    messageId = MessageId(3L),
                    identity = Identities.OTHER_1.value,
                ),
                ContactMessageIdentifier(
                    messageId = MessageId(4L),
                    identity = Identities.OTHER_2.value,
                ),
            ),
        )

        // assert
        assertFalse(anyExists)
    }

    @Test
    fun anyExists_single() {
        // arrange
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 1L,
                messageUid = "1",
                identity = Identities.OTHER_1,
            ),
        )
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 2L,
                messageUid = "2",
                identity = Identities.OTHER_2,
            ),
        )
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 3L,
                messageUid = "3",
                identity = Identities.OTHER_3,
            ),
        )

        // act
        val anyExists = contactMessageModelFactory.anyExists(
            setOf(
                ContactMessageIdentifier(
                    messageId = MessageId(1L),
                    identity = Identities.OTHER_1.value,
                ),
                ContactMessageIdentifier(
                    messageId = MessageId(4L),
                    identity = Identities.OTHER_1.value,
                ),
                ContactMessageIdentifier(
                    messageId = MessageId(5L),
                    identity = Identities.OTHER_2.value,
                ),
            ),
        )

        // assert
        assertTrue(anyExists)
    }

    @Test
    fun anyExists_multiple() {
        // arrange
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 1L,
                messageUid = "1",
                identity = Identities.OTHER_1,
            ),
        )
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 2L,
                messageUid = "2",
                identity = Identities.OTHER_2,
            ),
        )
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 3L,
                messageUid = "3",
                identity = Identities.OTHER_3,
            ),
        )

        // act
        val anyExists = contactMessageModelFactory.anyExists(
            setOf(
                ContactMessageIdentifier(
                    messageId = MessageId(1L),
                    identity = Identities.OTHER_1.value,
                ),
                ContactMessageIdentifier(
                    messageId = MessageId(4L),
                    identity = Identities.OTHER_1.value,
                ),
                ContactMessageIdentifier(
                    messageId = MessageId(3L),
                    identity = Identities.OTHER_3.value,
                ),
            ),
        )

        // assert
        assertTrue(anyExists)
    }

    @Test
    fun anyExists_all() {
        // arrange
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 1L,
                messageUid = "1",
                identity = Identities.OTHER_1,
            ),
        )
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 2L,
                messageUid = "2",
                identity = Identities.OTHER_2,
            ),
        )
        contactMessageModelFactory.create(
            createMessageModel(
                apiMessageId = 3L,
                messageUid = "3",
                identity = Identities.OTHER_3,
            ),
        )

        // act
        val anyExists = contactMessageModelFactory.anyExists(
            setOf(
                ContactMessageIdentifier(
                    messageId = MessageId(2L),
                    identity = Identities.OTHER_2.value,
                ),
                ContactMessageIdentifier(
                    messageId = MessageId(3L),
                    identity = Identities.OTHER_3.value,
                ),
            ),
        )

        // assert
        assertTrue(anyExists)
    }

    private companion object {

        /**
         * @param messageUid The message's uid (globally unique)
         * @param identity   Either the recipient (outgoing message) or the sender (incoming message)
         */
        fun createMessageModel(
            apiMessageId: Long,
            messageUid: String,
            identity: Identity,
            isOutbox: Boolean = true,
        ) = ContactMessageModel().apply {
            this.messageId = MessageId(apiMessageId)
            this.uid = messageUid
            this.postedAt = utcDate(2026, 7, 28)
            this.createdAt = utcDate(2026, 7, 28)
            this.editedAt = utcDate(2026, 7, 29)
            this.isOutbox = isOutbox
            this.isSaved = true
            this.identity = identity.value
            this.type = MessageType.TEXT
            this.body = "Message body"
        }
    }
}
