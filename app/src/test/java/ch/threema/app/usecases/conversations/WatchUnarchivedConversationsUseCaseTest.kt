package ch.threema.app.usecases.conversations

import app.cash.turbine.test
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.services.ConversationService
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.unconfinedTestDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import testdata.TestData

class WatchUnarchivedConversationsUseCaseTest {

    @Test
    fun `emits correct values`() = runTest {
        // arrange
        val conversationEvents = MutableSharedFlow<ConversationEvent>()
        val globalEventFlowsMock = mockk<GlobalEventFlows> {
            every { conversations } returns conversationEvents
        }
        val contactConversation = TestData.createContactConversationModel(identity = TestData.Identities.OTHER_1)
        val groupConversation = TestData.createGroupConversationModel(groupDatabaseId = 1L)
        val distributionListConversation = TestData.createDistributionListConversationModel(distributionListId = 1L)
        val initialConversations = listOf(contactConversation, groupConversation, distributionListConversation)
        val conversationService: ConversationService = mockk {
            every { getAll(any()) } returns initialConversations
        }
        val useCase = WatchUnarchivedConversationsUseCase(
            conversationService = conversationService,
            globalEventFlows = globalEventFlowsMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act/assert
        useCase.call().test {
            // Expect the current items
            expectItem(initialConversations)

            // Adding a new conversation
            val newConversation = TestData.createContactConversationModel(identity = TestData.Identities.OTHER_1)
            every { conversationService.getAll(any()) } returns initialConversations + newConversation
            conversationEvents.emit(ConversationEvent.NewConversation(newConversation))
            expectItem(initialConversations + newConversation)

            // Modifying an existing conversation
            val modifiedConversation = TestData.createGroupConversationModel(groupDatabaseId = 1L)
            every { conversationService.getAll(any()) } returns listOf(
                contactConversation,
                modifiedConversation,
                distributionListConversation,
            )
            conversationEvents.emit(ConversationEvent.ConversationUpdated(modifiedConversation))
            expectItem(listOf(contactConversation, modifiedConversation, distributionListConversation))

            // Modifying all existing conversations
            every { conversationService.getAll(any()) } returns initialConversations
            conversationEvents.emit(ConversationEvent.AllConversationsUpdated)
            expectItem(initialConversations)

            // Removing an existing conversation
            every { conversationService.getAll(any()) } returns listOf(groupConversation, distributionListConversation)
            conversationEvents.emit(ConversationEvent.ConversationRemoved(contactConversation))
            expectItem(listOf(groupConversation, distributionListConversation))
        }
    }
}
