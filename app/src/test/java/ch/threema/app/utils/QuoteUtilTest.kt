package ch.threema.app.utils

import ch.threema.app.R
import ch.threema.app.conversation.MessageViewElementFactory
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.FileService
import ch.threema.app.services.MessageService
import ch.threema.app.services.UserService
import ch.threema.app.test.koinTestModuleRule
import ch.threema.app.ui.models.MessageViewElement
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.domain.models.MessageId
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.test.TestContext
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import testdata.TestData

class QuoteUtilTest {

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory<MessageViewElementFactory> {
            mockk {
                every {
                    getViewElement(any(), any(), any(), any(), any())
                } answers {
                    val type = firstArg<MessageType>()
                    val body = secondArg<String>()
                    if (type == MessageType.FILE && body.contains("video/mpeg")) {
                        MessageViewElement(icon = R.drawable.ic_movie_filled, text = "Video")
                    } else {
                        MessageViewElement(text = body)
                    }
                }
            }
        }
    }

    @Test
    fun `extract quote text`() {
        // Test data
        val quoterIdentity = "AABBCCDD"
        val quotedIdentity = "AABBCCDD"
        val quotedMessageId = TestData.MessageIds.MESSAGE_ID_1
        val quoteMessageId = TestData.MessageIds.MESSAGE_ID_2
        val quotedBody = "Shall we eat lunch?"
        val quoteComment = "That's a great idea!"

        // Create quoted model
        val quotedModel: MessageModel = ContactMessageModel()
        quotedModel.messageId = quotedMessageId
        quotedModel.identity = quotedIdentity
        quotedModel.body = quotedBody
        quotedModel.type = MessageType.TEXT

        // Create quoter model
        val quoterModel: MessageModel = ContactMessageModel()
        quoterModel.messageId = quoteMessageId
        quoterModel.identity = quoterIdentity
        quoterModel.quotedMessageId = quotedMessageId
        quoterModel.body = quoteComment
        quoterModel.type = MessageType.TEXT

        // Verify
        val content = extractQuote(quoterModel, quotedModel)
        assertNotNull(content)
        assertEquals(quotedBody, content.quotedText)
        assertEquals(quoteComment, content.bodyText)
        assertEquals(quotedIdentity, content.identity)
        assertEquals(quotedMessageId, content.quotedMessageId)
        assertEquals(contactReceiverMock, content.messageReceiver)
        assertNull(content.quotedMessageModel)
        assertNull(content.thumbnail)
        assertNull(content.icon)
    }

    @Test
    fun `extract quote text, no receiver match`() {
        // Test data
        val quoterIdentity = "AABBCCDD"
        val quotedIdentity = "CCDDEEFF"
        val quotedMessageId = TestData.MessageIds.MESSAGE_ID_1
        val quoteMessageId = TestData.MessageIds.MESSAGE_ID_2
        val quotedBody = "Shall we eat lunch?"
        val quoteComment = "That's a great idea!"

        // Create quoted model
        val quotedModel: MessageModel = ContactMessageModel()
        quotedModel.messageId = quotedMessageId
        quotedModel.identity = quotedIdentity
        quotedModel.body = quotedBody
        quotedModel.type = MessageType.TEXT

        // Create quoter model
        val quoterModel: MessageModel = ContactMessageModel()
        quoterModel.messageId = quoteMessageId
        quoterModel.identity = quoterIdentity
        quoterModel.quotedMessageId = quotedMessageId
        quoterModel.body = quoteComment
        quoterModel.type = MessageType.TEXT

        // Verify
        val content = extractQuote(quoterModel, quotedModel)
        assertNotNull(content)
        assertEquals(TestContext.getString(R.string.quote_not_found), content.quotedText)
        assertEquals(quoteComment, content.bodyText)
        assertEquals(quotedMessageId, content.quotedMessageId)
        assertNotEquals(quotedIdentity, content.identity)
        assertNull(content.messageReceiver)
        assertNull(content.quotedMessageModel)
        assertNull(content.thumbnail)
        assertNull(content.icon)
    }

    @Test
    fun `extract quote text for video message, include message model`() {
        // Test data
        val quoterIdentity = "AABBCCDD"
        val quotedIdentity = "AABBCCDD"
        val quotedMessageId = TestData.MessageIds.MESSAGE_ID_1
        val quoteMessageId = TestData.MessageIds.MESSAGE_ID_2
        val quoteComment = "That's a great idea!"

        // Create quoted model
        val quotedModel: MessageModel = ContactMessageModel()
        quotedModel.messageId = quotedMessageId
        quotedModel.identity = quotedIdentity
        quotedModel.type = MessageType.FILE
        quotedModel.body = """["","","video/mpeg",512000,"le-video.mp4",1,true,null,null,{}]""" // video file, no caption

        // Create quoter model
        val quoterModel: MessageModel = ContactMessageModel()
        quoterModel.messageId = quoteMessageId
        quoterModel.identity = quoterIdentity
        quoterModel.quotedMessageId = quotedMessageId
        quoterModel.body = quoteComment
        quoterModel.type = MessageType.TEXT

        // Verify
        val content = extractQuote(quoterModel, quotedModel, includeMessageModel = true)
        assertNotNull(content)
        assertEquals("Video", content.quotedText)
        assertEquals(quoteComment, content.bodyText)
        assertEquals(quotedIdentity, content.identity)
        assertEquals(quotedMessageId, content.quotedMessageId)
        assertEquals(quotedModel, content.quotedMessageModel)
        assertEquals(contactReceiverMock, content.messageReceiver)
        assertNull(content.thumbnail)
        assertEquals(R.drawable.ic_movie_filled, content.icon)
    }

    @Test
    fun `extract quote for deleted message`() {
        // Test data
        val quoterIdentity = "AABBCCDD"
        val quotedMessageId = TestData.MessageIds.MESSAGE_ID_1
        val quoteMessageId = "8899889988998899"
        val quoteComment = "That's a great idea!"

        // Create quoter model with an invalid message id
        val quoterModel: MessageModel = ContactMessageModel()
        quoterModel.apiMessageId = quoteMessageId
        quoterModel.identity = quoterIdentity
        quoterModel.quotedMessageId = quotedMessageId
        quoterModel.body = quoteComment
        quoterModel.type = MessageType.TEXT

        // Verify
        val content = extractQuote(quoterModel, quotedModel = null, includeMessageModel = true)
        assertNotNull(content)
        assertEquals(TestContext.getString(R.string.quoted_message_deleted), content.quotedText)
        assertEquals(quoteComment, content.bodyText)
        assertNull(content.identity)
        assertEquals(quotedMessageId, content.quotedMessageId)
        assertNull(content.quotedMessageModel)
        assertNull(content.messageReceiver)
        assertNull(content.thumbnail)
        assertNull(content.icon)
    }

    @Test
    fun `get body and quoted message id from message`() {
        val (text, quotedMessageId) = QuoteUtil.getBodyAndQuotedMessageId(
            "> quote #abcdef0123456789\n\nI am quoting",
        )
        assertEquals("I am quoting", text)
        assertEquals(MessageId.fromString("abcdef0123456789"), quotedMessageId)
    }

    @Test
    fun `get body and quoted message id from message that has no quote`() {
        val (text, quotedMessageId) = QuoteUtil.getBodyAndQuotedMessageId("> no quote here")
        assertEquals("> no quote here", text)
        assertNull(quotedMessageId)
    }

    @Test
    fun `check body if it is a quote`() {
        assertFalse(QuoteUtil.isQuote(null as String?))
        assertFalse(QuoteUtil.isQuote("> Not a quote"))
        assertFalse(QuoteUtil.isQuote("> quote #123\n\nFake quote"))
        assertTrue(QuoteUtil.isQuote("> quote #abcdef0123456789\n\nReal quote"))
    }

    @Test
    fun `create quoted message body`() {
        val body = QuoteUtil.createQuotedMessageBody(
            text = "I am quoting",
            messageId = TestData.MessageIds.MESSAGE_ID_1,
        )
        assertEquals(
            "> quote #${TestData.MessageIds.MESSAGE_ID_1}\n\nI am quoting",
            body,
        )
    }

    companion object {
        private val contextMock = TestContext.create()

        private val contactReceiverMock = mockk<MessageReceiver<*>> {
            every { type } returns MessageReceiver.Type_CONTACT
        }

        /**
         * @param quoterModel The model that contains the quote.
         * @param quotedModel The quoted model.
         */
        private fun extractQuote(
            quoterModel: MessageModel,
            quotedModel: MessageModel?,
            includeMessageModel: Boolean = false,
        ): QuoteUtil.QuoteContent {
            // Mocks
            val mockMessageService = mockk<MessageService>()
            val mockUserService = mockk<UserService>()
            val mockFileService = mockk<FileService>()

            // Ensure that message service returns the correct models
            every {
                mockMessageService.getMessageModelByApiMessageIdAndReceiver(any(), contactReceiverMock)
            } returns null
            every {
                mockMessageService.getMessageModelByApiMessageIdAndReceiver(quoterModel.apiMessageId, contactReceiverMock)
            } returns quoterModel
            if (quotedModel != null) {
                every {
                    mockMessageService.getMessageModelByApiMessageIdAndReceiver(quotedModel.apiMessageId, contactReceiverMock)
                } returns quotedModel
            }

            // Return quote contents
            val quoteUtil = QuoteUtil(
                context = contextMock,
                userService = mockUserService,
                fileService = mockFileService,
                messageService = mockMessageService,
                preferenceService = mockk {
                    every { getContactNameFormat() } returns ContactNameFormat.DEFAULT
                },
            )
            return quoteUtil.getQuoteContent(
                messageModel = quoterModel,
                messageReceiver = contactReceiverMock,
                includeMessageModel = includeMessageModel,
            )!!
        }
    }
}
