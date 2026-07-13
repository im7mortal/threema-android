package ch.threema.app.usecases.conversations

import app.cash.turbine.test
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.services.ConversationService
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.unconfinedTestDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import testdata.TestData

class WatchArchivedConversationsUseCaseTest {

    // TODO(ANDR-4175): Test the skip of onNew for a non-archived conversation
    @Test
    fun `emits correct values and skips unnecessary changes`() = runTest {
        // arrange
        val conversationEvents = MutableSharedFlow<ConversationEvent>()
        val globalEventFlowsMock = mockk<GlobalEventFlows> {
            every { conversations } returns conversationEvents
        }
        val conversationService = mockk<ConversationService>()
        val useCase = WatchArchivedConversationsUseCase(
            conversationService = conversationService,
            globalEventFlows = globalEventFlowsMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        val archivedContactConversation = TestData.createContactConversationModel(
            identity = TestData.Identities.OTHER_1,
            conversationVisibility = ConversationVisibility.ARCHIVED,
        )
        val archivedGroupConversation = TestData.createGroupConversationModel(
            groupDatabaseId = 1L,
            conversationVisibility = ConversationVisibility.ARCHIVED,
        )
        val archivedDistributionListConversation = TestData.createDistributionListConversationModel(
            distributionListId = 1L,
            conversationVisibility = ConversationVisibility.ARCHIVED,
        )

        every { conversationService.archived } returns listOf(
            archivedContactConversation,
            archivedGroupConversation,
            archivedDistributionListConversation,
        )

        // act / assert
        useCase.call().test {
            // Expect the current items
            expectItem(listOf(archivedContactConversation, archivedGroupConversation, archivedDistributionListConversation))

            // Adding a new archived conversation
            val newArchivedConversation = TestData.createContactConversationModel(
                identity = TestData.Identities.OTHER_1,
                conversationVisibility = ConversationVisibility.ARCHIVED,
            )
            every { conversationService.archived } returns listOf(
                archivedContactConversation,
                archivedGroupConversation,
                archivedDistributionListConversation,
                newArchivedConversation,
            )
            conversationEvents.emit(ConversationEvent.NewConversation(newArchivedConversation))
            expectItem(
                listOf(
                    archivedContactConversation,
                    archivedGroupConversation,
                    archivedDistributionListConversation,
                    newArchivedConversation,
                ),
            )

            // Updating a non-archived conversation
            val updatedNonArchivedConversation = TestData.createGroupConversationModel(groupDatabaseId = 1L)
            conversationEvents.emit(ConversationEvent.ConversationUpdated(updatedNonArchivedConversation))
            expectNoEvents()

            // Updating an archived conversation
            val updatedArchivedConversation = TestData.createGroupConversationModel(
                groupDatabaseId = 1L,
                conversationVisibility = ConversationVisibility.ARCHIVED,
            )
            conversationEvents.emit(ConversationEvent.ConversationUpdated(updatedArchivedConversation))
            expectItem(listOf(archivedContactConversation, archivedGroupConversation, archivedDistributionListConversation, newArchivedConversation))

            // Modified all
            conversationEvents.emit(ConversationEvent.AllConversationsUpdated)
            expectItem(listOf(archivedContactConversation, archivedGroupConversation, archivedDistributionListConversation, newArchivedConversation))

            // Remove a non-archived conversation
            val removedNonArchivedConversation = TestData.createContactConversationModel(identity = TestData.Identities.OTHER_1)
            conversationEvents.emit(ConversationEvent.ConversationRemoved(removedNonArchivedConversation))
            expectNoEvents()

            // Remove an archived conversation
            val removedArchivedConversation = TestData.createContactConversationModel(
                identity = TestData.Identities.OTHER_1,
                conversationVisibility = ConversationVisibility.ARCHIVED,
            )
            every { conversationService.archived } returns listOf(
                archivedContactConversation,
                archivedGroupConversation,
                archivedDistributionListConversation,
            )
            conversationEvents.emit(ConversationEvent.ConversationRemoved(removedArchivedConversation))
            expectItem(listOf(archivedContactConversation, archivedGroupConversation, archivedDistributionListConversation))
        }

        verify(exactly = 5) { conversationService.archived }
    }
}
