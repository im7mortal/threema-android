package ch.threema.storage.models

import ch.threema.common.minus
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.storage.models.data.MessageContentsType
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import testdata.TestData

class ModelExtensionsTest {

    @Test
    fun `get file name`() {
        assertNull(
            mockk<MessageModel> {
                every { type } returns MessageType.TEXT
            }
                .getFileName(),
        )
        assertEquals(
            "my-file.pdf",
            mockk<MessageModel> {
                every { type } returns MessageType.FILE
                every { fileData } returns mockk {
                    every { fileName } returns "my-file.pdf"
                }
            }
                .getFileName(),
        )
    }

    @Test
    fun `get conversation id`() {
        assertEquals(
            ContactConversationId(TestData.Identities.OTHER_1.value),
            mockk<ContactMessageModel> {
                every { identity } returns TestData.Identities.OTHER_1.value
            }
                .getConversationId(),
        )
        assertEquals(
            GroupConversationId(groupDatabaseId = 123L),
            mockk<GroupMessageModel> {
                every { groupId } returns 123
            }
                .getConversationId(),
        )
        assertEquals(
            DistributionListConversationId(distributionListId = 456L),
            mockk<DistributionListMessageModel> {
                every { distributionListId } returns 456L
            }
                .getConversationId(),
        )

        assertFailsWith<IllegalArgumentException> {
            mockk<ContactMessageModel> {
                every { identity } returns null
            }
                .getConversationId()
        }
    }

    @Test
    fun `messages belong to same conversation`() {
        val contactMessage1 = mockk<ContactMessageModel> {
            every { identity } returns TestData.Identities.OTHER_1.value
        }
        val contactMessage2 = mockk<ContactMessageModel> {
            every { identity } returns TestData.Identities.OTHER_1.value
        }
        val contactMessage3 = mockk<ContactMessageModel> {
            every { identity } returns TestData.Identities.OTHER_3.value
        }
        assertTrue(contactMessage1.belongsToSameConversationAs(contactMessage2))
        assertTrue(contactMessage2.belongsToSameConversationAs(contactMessage1))
        assertFalse(contactMessage1.belongsToSameConversationAs(contactMessage3))

        val groupMessage1 = mockk<GroupMessageModel> {
            every { groupId } returns 1
        }
        val groupMessage2 = mockk<GroupMessageModel> {
            every { groupId } returns 1
        }
        val groupMessage3 = mockk<GroupMessageModel> {
            every { groupId } returns 3
        }
        assertTrue(groupMessage1.belongsToSameConversationAs(groupMessage2))
        assertTrue(groupMessage2.belongsToSameConversationAs(groupMessage1))
        assertFalse(groupMessage1.belongsToSameConversationAs(groupMessage3))

        val distributionListMessage1 = mockk<DistributionListMessageModel> {
            every { distributionListId } returns 1L
        }
        val distributionListMessage2 = mockk<DistributionListMessageModel> {
            every { distributionListId } returns 1L
        }
        val distributionListMessage3 = mockk<DistributionListMessageModel> {
            every { distributionListId } returns 3L
        }
        assertTrue(distributionListMessage1.belongsToSameConversationAs(distributionListMessage2))
        assertTrue(distributionListMessage2.belongsToSameConversationAs(distributionListMessage1))
        assertFalse(distributionListMessage1.belongsToSameConversationAs(distributionListMessage3))

        assertFalse(contactMessage1.belongsToSameConversationAs(groupMessage1))
        assertFalse(contactMessage1.belongsToSameConversationAs(distributionListMessage1))
        assertFalse(groupMessage1.belongsToSameConversationAs(distributionListMessage1))
    }

    @Test
    fun `check message if it is a quote`() {
        assertFalse(
            mockk<MessageModel> {
                every { quotedMessageIdString } returns null
            }
                .isQuote(),
        )
        assertTrue(
            mockk<MessageModel> {
                every { quotedMessageIdString } returns TestData.MessageIds.MESSAGE_ID_1.toString()
            }
                .isQuote(),
        )
    }

    @Test
    fun `is quotable`() {
        assertFalse(
            mockk<MessageModel> {
                every { isDeleted } returns true
            }
                .isQuotable(),
        )
        listOf(
            MessageType.STATUS,
            MessageType.GROUP_STATUS,
            MessageType.GROUP_CALL_STATUS,
            MessageType.VOIP_STATUS,
            MessageType.FORWARD_SECURITY_STATUS,
        ).forEach { messageType ->
            assertFalse(
                mockk<MessageModel> {
                    every { isDeleted } returns false
                    every { type } returns messageType
                    every { apiMessageId } returns TestData.MessageIds.MESSAGE_ID_1.toString()
                }
                    .isQuotable(),
            )
        }
        listOf(
            MessageType.TEXT,
            MessageType.FILE,
            MessageType.POLL,
            MessageType.LOCATION,
        ).forEach { messageType ->
            assertFalse(
                mockk<MessageModel> {
                    every { isDeleted } returns false
                    every { type } returns messageType
                    every { apiMessageId } returns null
                }
                    .isQuotable(),
            )
            assertTrue(
                mockk<MessageModel> {
                    every { isDeleted } returns false
                    every { type } returns messageType
                    every { apiMessageId } returns TestData.MessageIds.MESSAGE_ID_1.toString()
                }
                    .isQuotable(),
            )
        }
    }

    @Test
    fun `text message can be edited`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isOutbox } returns true
            every { createdAt } returns Instant.now()
            every { postedAt } returns Instant.now()
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertTrue(message.canBeEdited())
    }

    @Test
    fun `file message can be edited`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.FILE
            every { isOutbox } returns true
            every { createdAt } returns Instant.now()
            every { postedAt } returns Instant.now()
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertTrue(message.canBeEdited())
    }

    @Test
    fun `message can be edited if it is less than 6 hours old`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isOutbox } returns true
            every { createdAt } returns Instant.now() - 5.9.hours
            every { postedAt } returns Instant.now()
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertTrue(message.canBeEdited())
    }

    @Test
    fun `message can be edited if it is less than 6 hours older than custom editTime`() {
        val editTime = Instant.now() - 4.hours
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isOutbox } returns true
            every { createdAt } returns editTime - 5.9.hours
            every { postedAt } returns Instant.now()
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertTrue(message.canBeEdited(editTime = editTime))
    }

    @Test
    fun `message can not be edited if it is more than 6 hours old`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isOutbox } returns true
            every { createdAt } returns Instant.now() - 6.1.hours
            every { postedAt } returns Instant.now()
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertFalse(message.canBeEdited())
    }

    @Test
    fun `message in notes group can still be edited even if it is older than 6 hours`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isOutbox } returns true
            every { createdAt } returns Instant.now() - 6.1.hours
            every { postedAt } returns Instant.now()
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertTrue(message.canBeEdited(belongsToNotesGroup = true))
    }

    @Test
    fun `status message can not be edited`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isStatusMessage } returns true
            every { isOutbox } returns true
            every { createdAt } returns Instant.now()
            every { postedAt } returns Instant.now()
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertFalse(message.canBeEdited())
    }

    @Test
    fun `message from other user can not be edited`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isOutbox } returns false
            every { createdAt } returns Instant.now()
            every { postedAt } returns Instant.now()
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertFalse(message.canBeEdited())
    }

    @Test
    fun `location message can not be edited`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.LOCATION
            every { isOutbox } returns true
            every { createdAt } returns Instant.now()
            every { postedAt } returns Instant.now()
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertFalse(message.canBeEdited())
    }

    @Test
    fun `deleted message can not be edited`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isOutbox } returns true
            every { createdAt } returns Instant.now()
            every { postedAt } returns Instant.now()
            every { isDeleted } returns true
            every { isStatusMessage } returns false
            every { state } returns MessageState.SENT
        }
        assertFalse(message.canBeEdited())
    }

    @Test
    fun `unposted message can not be edited`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isOutbox } returns true
            every { createdAt } returns Instant.now()
            every { postedAt } returns null
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { state } returns MessageState.SENT
        }
        assertFalse(message.canBeEdited())
    }

    @Test
    fun `unposted message can not be edited if it failed to send`() {
        val message = mockk<ContactMessageModel> {
            every { type } returns MessageType.TEXT
            every { isOutbox } returns true
            every { createdAt } returns Instant.now()
            every { postedAt } returns null
            every { state } returns MessageState.SENDFAILED
            every { isStatusMessage } returns false
            every { isDeleted } returns false
        }
        assertTrue(message.canBeEdited())
    }

    @Test
    fun `group message can be edited (conditions apply)`() {
        val message = mockk<GroupMessageModel> {
            every { type } returns MessageType.TEXT
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { isOutbox } returns true
            every { createdAt } returns Instant.now()
            every { postedAt } returns Instant.now()
            every { state } returns MessageState.SENT
        }

        assertTrue(message.canBeEdited())
    }

    @Test
    fun `distribution list message can not be edited`() {
        val message = mockk<DistributionListMessageModel> {
            every { type } returns MessageType.TEXT
            every { isStatusMessage } returns false
            every { isDeleted } returns false
            every { isOutbox } returns true
            every { createdAt } returns Instant.now()
            every { postedAt } returns Instant.now()
            every { state } returns MessageState.SENT
        }

        assertFalse(message.canBeEdited())
    }

    @Test
    fun `contact message can be starred`() {
        arrayOf(
            MessageType.TEXT,
            MessageType.FILE,
            MessageType.LOCATION,
            MessageType.POLL,
        )
            .forEach { messageType ->
                val message = mockk<ContactMessageModel> {
                    every { type } returns messageType
                }
                assertTrue(message.canBeStarred())
            }
    }

    @Test
    fun `group message can be starred`() {
        arrayOf(
            MessageType.TEXT,
            MessageType.FILE,
            MessageType.LOCATION,
            MessageType.POLL,
        )
            .forEach { messageType ->
                val message = mockk<GroupMessageModel> {
                    every { type } returns messageType
                }
                assertTrue(message.canBeStarred())
            }
    }

    @Test
    fun `status messages can not be starred`() {
        arrayOf(
            MessageType.STATUS,
            MessageType.GROUP_STATUS,
            MessageType.VOIP_STATUS,
            MessageType.GROUP_CALL_STATUS,
            MessageType.FORWARD_SECURITY_STATUS,
        )
            .forEach { messageType ->
                val message = mockk<GroupMessageModel> {
                    every { type } returns messageType
                }
                assertFalse(message.canBeStarred())
            }
    }

    @Test
    fun `distribution list message can not be starred`() {
        val message = mockk<DistributionListMessageModel>()
        assertFalse(message.canBeStarred())
    }

    @Test
    fun `can send image reply for downloaded image message`() {
        val message = mockk<ContactMessageModel> {
            every { messageContentsType } returns MessageContentsType.IMAGE
            every { isDeleted } returns false
            every { fileData } returns mockk {
                every { isDownloaded } returns true
            }
        }
        assertTrue(message.canSendImageReply())
    }

    @Test
    fun `cannot send image reply for non-downloaded image message`() {
        val message = mockk<ContactMessageModel> {
            every { messageContentsType } returns MessageContentsType.IMAGE
            every { isDeleted } returns false
            every { fileData } returns mockk {
                every { isDownloaded } returns false
            }
        }
        assertFalse(message.canSendImageReply())
    }

    @Test
    fun `cannot send image reply for deleted image message`() {
        val message = mockk<ContactMessageModel> {
            every { messageContentsType } returns MessageContentsType.IMAGE
            every { isDeleted } returns true
        }
        assertFalse(message.canSendImageReply())
    }

    @Test
    fun `cannot send image reply for non-image message`() {
        val message = mockk<ContactMessageModel> {
            every { messageContentsType } returns MessageContentsType.FILE
        }
        assertFalse(message.canSendImageReply())
    }
}
