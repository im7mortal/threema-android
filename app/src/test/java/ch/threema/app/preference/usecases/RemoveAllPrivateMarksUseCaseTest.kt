package ch.threema.app.preference.usecases

import ch.threema.app.eventbus.EventBus
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.eventbus.events.DistributionListEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.DistributionListService
import ch.threema.app.widget.WidgetUpdater
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.ContactModel
import ch.threema.data.models.GroupModel
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.DistributionListModel
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import testdata.TestData

class RemoveAllPrivateMarksUseCaseTest {

    private fun createMockedContactMessageReceiver(contactModel: ContactModel): ContactMessageReceiver =
        mockk<ContactMessageReceiver> contactMessageReceiver@{
            every { conversationId } returns ContactConversationId(contactModel.identity)
            every { type } returns MessageReceiver.Type_CONTACT
            every { this@contactMessageReceiver.contactModel } returns contactModel
        }

    private fun createMockedGroupMessageReceiver(groupModel: GroupModel) =
        mockk<GroupMessageReceiver> groupMessageReceiver@{
            every { conversationId } returns GroupConversationId(groupModel.getDatabaseId())
            every { type } returns MessageReceiver.Type_GROUP
            every { this@groupMessageReceiver.groupModel } returns groupModel
        }

    private fun createMockedDistributionListMessageReceiver(distributionListModel: DistributionListModel) =
        mockk<DistributionListMessageReceiver> distributionListMessageReceiver@{
            every { conversationId } returns DistributionListConversationId(distributionListModel.id)
            every { type } returns MessageReceiver.Type_DISTRIBUTION_LIST
            every { this@distributionListMessageReceiver.distributionList } returns distributionListModel
        }

    @Test
    fun `remove private marks`() {
        val contactModelMock: ContactModel = mockk {
            every { identity } returns TestData.Identities.OTHER_1.value
            every { setConversationCategoryFromLocalOrRemote(ConversationCategory.DEFAULT) } just Runs
        }
        val contactReceiver = createMockedContactMessageReceiver(
            contactModel = contactModelMock,
        )
        val archivedContactModelMock: ContactModel = mockk {
            every { identity } returns TestData.Identities.OTHER_2.value
            every { setConversationCategoryFromLocalOrRemote(ConversationCategory.DEFAULT) } just Runs
        }
        val archivedContactReceiver = createMockedContactMessageReceiver(
            contactModel = archivedContactModelMock,
        )
        val groupIdentity = GroupIdentity(
            creatorIdentity = TestData.Identities.OTHER_1.value,
            groupId = 1L,
        )
        val groupModelMock: GroupModel = mockk groupModelMock@{
            every { getDatabaseId() } returns 42
            every { this@groupModelMock.groupIdentity } returns groupIdentity
            every { setConversationCategoryFromLocalOrRemote(ConversationCategory.DEFAULT) } just Runs
        }
        val groupReceiver = createMockedGroupMessageReceiver(
            groupModel = groupModelMock,
        )
        val distributionListMock = mockk<DistributionListModel> {
            every { id } returns 1L
            every { setConversationCategory(ConversationCategory.DEFAULT) } returns this
        }
        val distributionListReceiver = createMockedDistributionListMessageReceiver(
            distributionListModel = distributionListMock,
        )
        val distributionListServiceMock: DistributionListService = mockk {
            every { setConversationCategory(distributionListMock, ConversationCategory.DEFAULT) } just Runs
        }

        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true)
        val widgetUpdaterMock = mockk<WidgetUpdater>(relaxed = true)
        val contactEventBusMock = mockk<EventBus<ContactEvent>>(relaxed = true)
        val conversationEventBusMock = mockk<EventBus<ConversationEvent>>(relaxed = true)
        val distributionListEventBusMock = mockk<EventBus<DistributionListEvent>>(relaxed = true)
        val groupEventBusMock = mockk<EventBus<GroupEvent>>(relaxed = true)
        val globalEventBusesMock = mockk<GlobalEventBuses> {
            every { contacts } returns contactEventBusMock
            every { conversations } returns conversationEventBusMock
            every { distributionLists } returns distributionListEventBusMock
            every { groups } returns groupEventBusMock
        }
        val useCase = RemoveAllPrivateMarksUseCase(
            conversationService = mockk {
                every { getAll(false) } returns listOf(
                    ConversationModel(contactReceiver),
                    ConversationModel(groupReceiver),
                    ConversationModel(distributionListReceiver),
                )
                every { archived } returns listOf(
                    ConversationModel(archivedContactReceiver),
                )
            },
            preferenceService = preferenceServiceMock,
            distributionListService = distributionListServiceMock,
            widgetUpdater = widgetUpdaterMock,
            globalEventBuses = globalEventBusesMock,
        )

        useCase.call()

        verify(exactly = 1) { preferenceServiceMock.setArePrivateChatsHidden(false) }
        verify(exactly = 1) { widgetUpdaterMock.updateWidgets() }
        verify(exactly = 1) { conversationEventBusMock.emit(ConversationEvent.AllConversationsUpdated) }
        verify(exactly = 1) { contactModelMock.setConversationCategoryFromLocalOrRemote(ConversationCategory.DEFAULT) }
        verify(exactly = 1) { archivedContactModelMock.setConversationCategoryFromLocalOrRemote(ConversationCategory.DEFAULT) }
        verify(exactly = 1) { groupModelMock.setConversationCategoryFromLocalOrRemote(ConversationCategory.DEFAULT) }
        verify(exactly = 1) { distributionListServiceMock.setConversationCategory(distributionListMock, ConversationCategory.DEFAULT) }
    }
}
