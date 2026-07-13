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
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.widget.WidgetUpdater
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.domain.models.GroupId
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.DistributionListModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import testdata.TestData

class RemoveAllPrivateMarksUseCaseTest {

    private fun createMockedContactMessageReceiver(identity: Identity): ContactMessageReceiver =
        mockk<ContactMessageReceiver> {
            every { conversationId } returns ContactConversationId(identity.value)
            every { type } returns MessageReceiver.Type_CONTACT
            every { contact } returns mockk contactModelMock@{
                every { this@contactModelMock.identity } returns identity.value
            }
        }

    private fun createMockedGroupMessageReceiver(
        groupDatabaseId: GroupDatabaseId,
        groupIdentity: GroupIdentity,
    ) = mockk<GroupMessageReceiver> {
        every { conversationId } returns GroupConversationId(groupDatabaseId)
        every { type } returns MessageReceiver.Type_GROUP
        every { group } returns mockk groupModelMock@{
            every { this@groupModelMock.groupIdentity } returns groupIdentity
            every { creatorIdentity } returns groupIdentity.creatorIdentity
            every { apiGroupId } returns GroupId(groupIdentity.groupId)
            every { id } returns groupDatabaseId.toInt()
        }
    }

    private fun createMockedDistributionListMessageReceiver(distributionList: DistributionListModel) =
        mockk<DistributionListMessageReceiver> distributionListMessageReceiver@{
            every { conversationId } returns DistributionListConversationId(distributionList.id)
            every { type } returns MessageReceiver.Type_DISTRIBUTION_LIST
            every { this@distributionListMessageReceiver.distributionList } returns distributionList
        }

    @Test
    fun `remove private marks`() {
        val privateContactReceiver = createMockedContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
        )
        val nonPrivateContactReceiver = createMockedContactMessageReceiver(
            identity = TestData.Identities.OTHER_2,
        )
        val privateGroupIdentity = GroupIdentity(
            creatorIdentity = TestData.Identities.OTHER_1.value,
            groupId = 1L,
        )
        val privateGroupReceiver = createMockedGroupMessageReceiver(
            groupDatabaseId = 1L,
            groupIdentity = privateGroupIdentity,
        )
        val nonPrivateGroupReceiver = createMockedGroupMessageReceiver(
            groupDatabaseId = 2L,
            groupIdentity = GroupIdentity(
                creatorIdentity = TestData.Identities.OTHER_2.value,
                groupId = 2L,
            ),
        )
        val privateDistributionList = mockk<DistributionListModel> {
            every { id } returns 1L
        }
        val privateDistributionListReceiver = createMockedDistributionListMessageReceiver(
            distributionList = privateDistributionList,
        )
        val nonPrivateDistributionListReceiver = createMockedDistributionListMessageReceiver(
            distributionList = mockk {
                every { id } returns 2L
            },
        )

        val conversationCategoryServiceMock = mockk<ConversationCategoryService> {
            every { removePrivateMark(privateContactReceiver.conversationId) } returns true
            every { removePrivateMark(nonPrivateContactReceiver.conversationId) } returns false
            every { removePrivateMark(privateGroupReceiver.conversationId) } returns true
            every { removePrivateMark(nonPrivateGroupReceiver.conversationId) } returns false
            every { removePrivateMark(privateDistributionListReceiver.conversationId) } returns true
            every { removePrivateMark(nonPrivateDistributionListReceiver.conversationId) } returns false
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
                    ConversationModel(nonPrivateContactReceiver),
                    ConversationModel(privateGroupReceiver),
                    ConversationModel(nonPrivateGroupReceiver),
                    ConversationModel(privateDistributionListReceiver),
                    ConversationModel(nonPrivateDistributionListReceiver),
                )
                every { archived } returns listOf(
                    ConversationModel(privateContactReceiver),
                )
            },
            conversationCategoryService = conversationCategoryServiceMock,
            preferenceService = preferenceServiceMock,
            widgetUpdater = widgetUpdaterMock,
            globalEventBuses = globalEventBusesMock,
        )

        useCase.call()

        verify(exactly = 1) { preferenceServiceMock.setArePrivateChatsHidden(false) }
        verify(exactly = 1) { widgetUpdaterMock.updateWidgets() }
        verify(exactly = 1) { conversationEventBusMock.emit(ConversationEvent.AllConversationsUpdated) }
        verify(exactly = 1) { contactEventBusMock.emit(ContactEvent.ContactUpdated(TestData.Identities.OTHER_1)) }
        verify(exactly = 1) { groupEventBusMock.emit(GroupEvent.GroupUpdated(privateGroupIdentity)) }
        verify(exactly = 1) { distributionListEventBusMock.emit(DistributionListEvent.DistributionListUpdated(privateDistributionList)) }
    }

    @Test
    fun `nothing happens when there are no private conversations`() {
        val nonPrivateContactReceiver = createMockedContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
        )
        val nonPrivateGroupReceiver = createMockedGroupMessageReceiver(
            groupDatabaseId = 1L,
            groupIdentity = GroupIdentity(
                creatorIdentity = TestData.Identities.OTHER_1.value,
                groupId = 1L,
            ),
        )
        val nonPrivateDistributionListReceiver = createMockedDistributionListMessageReceiver(
            distributionList = mockk {
                every { id } returns 1L
            },
        )
        val conversationCategoryServiceMock = mockk<ConversationCategoryService> {
            every { removePrivateMark(nonPrivateContactReceiver.conversationId) } returns false
            every { removePrivateMark(nonPrivateGroupReceiver.conversationId) } returns false
            every { removePrivateMark(nonPrivateDistributionListReceiver.conversationId) } returns false
        }
        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true)
        val widgetUpdaterMock = mockk<WidgetUpdater>(relaxed = true)
        val useCase = RemoveAllPrivateMarksUseCase(
            conversationService = mockk {
                every { getAll(false) } returns listOf(
                    ConversationModel(nonPrivateContactReceiver),
                    ConversationModel(nonPrivateGroupReceiver),
                    ConversationModel(nonPrivateDistributionListReceiver),
                )
                every { archived } returns emptyList()
            },
            conversationCategoryService = conversationCategoryServiceMock,
            preferenceService = preferenceServiceMock,
            widgetUpdater = widgetUpdaterMock,
            globalEventBuses = mockk(),
        )

        useCase.call()

        verify(exactly = 0) { preferenceServiceMock.setArePrivateChatsHidden(false) }
        verify(exactly = 0) { widgetUpdaterMock.updateWidgets() }
    }
}
