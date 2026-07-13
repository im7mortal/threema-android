package ch.threema.app.usecases.conversation

import ch.threema.app.services.ConversationService
import ch.threema.app.services.MessageService
import ch.threema.app.services.notification.NotificationService
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.domain.types.Identity
import ch.threema.storage.DatabaseService
import ch.threema.storage.factories.MessageModelFactory
import ch.threema.storage.models.MessageModel
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.called
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import testdata.TestData
import testdata.TestData.Identities

class MarkConversationAsReadUseCaseTest {

    @Test
    fun `does nothing if conversation does not exist`() = runTest {
        // arrange
        val conversationServiceMock = mockk<ConversationService> {
            every { this@mockk.get(any<ConversationId>()) } returns null
        }
        val messageServiceMock = mockk<MessageService>()
        val notificationServiceMock = mockk<NotificationService>()
        val useCase = MarkConversationAsReadUseCase(
            conversationService = conversationServiceMock,
            messageService = messageServiceMock,
            notificationService = notificationServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        // act
        val conversationId = ContactConversationId(identity = Identities.OTHER_1.value)
        useCase.call(conversationId)

        // assert
        verify(exactly = 1) {
            conversationServiceMock.get(conversationId)
        }
        verify { messageServiceMock wasNot called }
        verify { notificationServiceMock wasNot called }
    }

    @Test
    fun `marks everything as read and cancels notification`() = runTest {
        // arrange
        val conversationId = ContactConversationId(identity = Identities.OTHER_1.value)
        val messageModelFactoryMock = mockk<MessageModelFactory> {
            every { getUnreadMessages(conversationId.identity) } returns emptyList<MessageModel>()
        }
        val databaseServiceMock = mockk<DatabaseService>(relaxed = true) {
            every { messageModelFactory } returns messageModelFactoryMock
        }
        val conversationModel = TestData.createContactConversationModel(
            identity = Identity(conversationId.identity),
            databaseServiceMock = databaseServiceMock,
        )
        val conversationServiceMock = mockk<ConversationService> {
            every { this@mockk.get(conversationId) } returns conversationModel
            every { markConversationAsRead(conversationModel.messageReceiver) } just runs
        }
        val messageServiceMock = mockk<MessageService>(relaxed = true)
        val notificationServiceMock = mockk<NotificationService>(relaxed = true)
        val useCase = MarkConversationAsReadUseCase(
            conversationService = conversationServiceMock,
            messageService = messageServiceMock,
            notificationService = notificationServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        // act
        useCase.call(conversationId)

        // assert
        verify(exactly = 1) {
            conversationServiceMock.get(conversationId)
            conversationServiceMock.markConversationAsRead(conversationModel.messageReceiver)
        }
        verify(exactly = 1) {
            notificationServiceMock.cancel(conversationId)
        }
    }
}
