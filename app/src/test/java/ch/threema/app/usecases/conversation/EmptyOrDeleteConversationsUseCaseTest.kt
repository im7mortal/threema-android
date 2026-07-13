package ch.threema.app.usecases.conversation

import ch.threema.app.groupflows.GroupDisbandIntent
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.groupflows.GroupLeaveIntent
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase.Mode
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase.Result.Completed.OperationResult
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.models.GroupModel
import ch.threema.domain.models.UserState
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Before
import testdata.TestData.Identities
import testdata.TestData.createContactConversationModel
import testdata.TestData.createDistributionListConversationModel
import testdata.TestData.createGroupConversationModel

@Suppress("DeferredResultUnused")
class EmptyOrDeleteConversationsUseCaseTest {

    private val identityProvider = TestIdentityProvider(
        identity = Identities.ME,
    )

    private lateinit var conversationServiceMock: ConversationService
    private lateinit var distributionListServiceMock: DistributionListService
    private lateinit var groupFlowDispatcherMock: GroupFlowDispatcher

    private fun createUseCase(testDispatcherProvider: DispatcherProvider) =
        EmptyOrDeleteConversationsUseCase(
            dispatcherProvider = testDispatcherProvider,
            conversationService = conversationServiceMock,
            distributionListService = distributionListServiceMock,
            groupFlowDispatcher = groupFlowDispatcherMock,
            identityProvider = identityProvider,
        )

    @Before
    fun before() {
        conversationServiceMock = mockk<ConversationService>()
        distributionListServiceMock = mockk<DistributionListService>()
        groupFlowDispatcherMock = mockk<GroupFlowDispatcher>()
    }

    @Test
    fun `does nothing if no conversations passed`() = runTest {
        // arrange
        val useCase = createUseCase(testDispatcherProvider())

        // act
        val resultModeEmpty = useCase.call(
            conversationIds = emptyList(),
            mode = Mode.EMPTY,
        )
        val resultModeDelete = useCase.call(
            conversationIds = emptyList(),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(emptyMap()),
            actual = resultModeEmpty,
        )
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(emptyMap()),
            actual = resultModeDelete,
        )
        confirmVerified(conversationServiceMock, distributionListServiceMock, groupFlowDispatcherMock)
    }

    @Test
    fun `does nothing if one of multiple passed conversations does not exist`() = runTest {
        // arrange
        val contactConversationExists = createContactConversationModel(
            ContactConversationId(identity = Identities.OTHER_1.value),
        )
        every {
            conversationServiceMock.get(contactConversationExists.id)
        } returns contactConversationExists

        val conversationIdNotExists = ContactConversationId(identity = Identities.OTHER_2.value)
        every {
            conversationServiceMock.get(conversationIdNotExists)
        } returns null

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val resultModeEmpty = useCase.call(
            conversationIds = listOf(contactConversationExists.id, conversationIdNotExists),
            mode = Mode.EMPTY,
        )
        val resultModeDelete = useCase.call(
            conversationIds = listOf(contactConversationExists.id, conversationIdNotExists),
            mode = Mode.EMPTY,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.UnknownConversation,
            actual = resultModeEmpty,
        )
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.UnknownConversation,
            actual = resultModeDelete,
        )
        verify(exactly = 2) { conversationServiceMock.get(contactConversationExists.id) }
        verify(exactly = 2) { conversationServiceMock.get(conversationIdNotExists) }
        confirmVerified(conversationServiceMock, distributionListServiceMock, groupFlowDispatcherMock)
    }

    @Test
    fun `empties a single contact conversation`() = runTest {
        // arrange
        val contactConversation = createAndMockContactConversationForEmpty(
            ContactConversationId(identity = Identities.OTHER_1.value),
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                contactConversation.id,
            ),
            mode = Mode.EMPTY,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    contactConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            conversationServiceMock.empty(
                /* conversation = */
                contactConversation,
                /* silentMessageUpdate = */
                any(),
            )
        }
        verify(exactly = 0) { conversationServiceMock.delete(Identities.OTHER_1.value) }
        verifyConversationsRefreshed(contactConversation)
    }

    @Test
    fun `empties a single group conversation`() = runTest {
        // arrange
        val groupConversation = createAndMockGroupConversationForEmpty(
            GroupConversationId(groupDatabaseId = 1L),
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                groupConversation.id,
            ),
            mode = Mode.EMPTY,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    groupConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            conversationServiceMock.empty(
                /* conversation = */
                groupConversation,
                /* silentMessageUpdate = */
                any(),
            )
        }
        verifyConversationsRefreshed(groupConversation)
    }

    @Test
    fun `empties a single distribution list conversation`() = runTest {
        // arrange
        val distributionListConversation = createAndMockDistributionListConversationForEmpty(
            DistributionListConversationId(distributionListId = 1L),
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                distributionListConversation.id,
            ),
            mode = Mode.EMPTY,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    distributionListConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            conversationServiceMock.empty(
                /* conversation = */
                distributionListConversation,
                /* silentMessageUpdate = */
                any(),
            )
        }
        verify(exactly = 0) { distributionListServiceMock.remove(any()) }
        verifyConversationsRefreshed(distributionListConversation)
    }

    @Test
    fun `empties multiple different conversation types`() = runTest {
        // arrange
        val contactOther1Conversation = createAndMockContactConversationForEmpty(
            ContactConversationId(identity = Identities.OTHER_1.value),
        )
        val contactOther2Conversation = createAndMockContactConversationForEmpty(
            ContactConversationId(identity = Identities.OTHER_2.value),
        )
        val groupConversation = createAndMockGroupConversationForEmpty(
            GroupConversationId(groupDatabaseId = 1L),
        )
        val distributionListConversation = createAndMockDistributionListConversationForEmpty(
            DistributionListConversationId(distributionListId = 1L),
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                contactOther1Conversation.id,
                contactOther2Conversation.id,
                groupConversation.id,
                distributionListConversation.id,
            ),
            mode = Mode.EMPTY,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    contactOther1Conversation.id to OperationResult.Success,
                    contactOther2Conversation.id to OperationResult.Success,
                    groupConversation.id to OperationResult.Success,
                    distributionListConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )

        verify(exactly = 4) {
            conversationServiceMock.empty(
                /* conversation = */
                match { conversationModel ->
                    conversationModel in setOf(
                        contactOther1Conversation,
                        contactOther2Conversation,
                        groupConversation,
                        distributionListConversation,
                    )
                },
                /* silentMessageUpdate = */
                any(),
            )
        }
        verify(exactly = 0) { conversationServiceMock.delete(any()) }
        verify(exactly = 0) { distributionListServiceMock.remove(any()) }

        verifyConversationsRefreshed(
            contactOther1Conversation,
            contactOther2Conversation,
            groupConversation,
            distributionListConversation,
        )

        confirmVerified(distributionListServiceMock, groupFlowDispatcherMock)
    }

    @Test
    fun `deletes a single contact conversation`() = runTest {
        // arrange
        val contactConversation = createAndMockContactConversationForDelete(
            ContactConversationId(identity = Identities.OTHER_1.value),
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                contactConversation.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    contactConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            conversationServiceMock.delete(
                /* identity = */
                Identities.OTHER_1.value,
            )
        }
        verify(exactly = 0) {
            conversationServiceMock.empty(
                /* conversation = */
                contactConversation,
                /* silentMessageUpdate = */
                any(),
            )
        }
        verifyConversationsRefreshed(contactConversation)
    }

    @Test
    fun `deletes a single group conversation, user member, user not creator`() = runTest {
        // arrange
        val groupConversation = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 1L),
            isCreator = false,
            isMember = true,
            groupFlowResultDeferred = { groupModel -> CompletableDeferred(GroupFlowResult.Success(groupModel)) },
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                groupConversation.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    groupConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            groupFlowDispatcherMock.runLeaveGroupFlow(
                intent = GroupLeaveIntent.LEAVE_AND_REMOVE,
                groupModel = groupConversation.groupModel!!,
            )
        }
        verifyConversationsRefreshed(groupConversation)
        confirmVerified(groupFlowDispatcherMock)
    }

    @Test
    fun `deletes a single group conversation, user member, user creator`() = runTest {
        // arrange
        val groupConversation = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 1L),
            isCreator = true,
            isMember = true,
            groupFlowResultDeferred = { groupModel -> CompletableDeferred(GroupFlowResult.Success(groupModel)) },
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                groupConversation.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    groupConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            groupFlowDispatcherMock.runDisbandGroupFlow(
                intent = GroupDisbandIntent.DISBAND_AND_REMOVE,
                groupModel = groupConversation.groupModel!!,
            )
        }
        verifyConversationsRefreshed(groupConversation)
        confirmVerified(groupFlowDispatcherMock)
    }

    @Test
    fun `deletes a single group conversation, user not member, user not creator`() = runTest {
        // arrange
        val groupConversation = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 1L),
            isCreator = false,
            isMember = false,
            groupFlowResultDeferred = { groupModel -> CompletableDeferred(GroupFlowResult.Success(groupModel)) },
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                groupConversation.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    groupConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            groupFlowDispatcherMock.runRemoveGroupFlow(groupConversation.groupModel!!)
        }
        verifyConversationsRefreshed(groupConversation)
        confirmVerified(groupFlowDispatcherMock)
    }

    @Test
    fun `deletes a single group conversation, user not member, user creator`() = runTest {
        // arrange
        val groupConversation = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 1L),
            isCreator = true,
            isMember = false,
            groupFlowResultDeferred = { groupModel -> CompletableDeferred(GroupFlowResult.Success(groupModel)) },
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                groupConversation.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    groupConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            groupFlowDispatcherMock.runRemoveGroupFlow(groupConversation.groupModel!!)
        }
        verifyConversationsRefreshed(groupConversation)
        confirmVerified(groupFlowDispatcherMock)
    }

    @Test
    fun `deletes multiple group conversations`() = runTest {
        // arrange
        val groupConversation1 = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 1L),
            isCreator = false,
            isMember = true,
            groupFlowResultDeferred = { groupModel -> CompletableDeferred(GroupFlowResult.Success(groupModel)) },
        )
        val groupConversation2 = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 2L),
            isCreator = false,
            isMember = true,
            groupFlowResultDeferred = { CompletableDeferred(GroupFlowResult.Failure.Network) },
        )
        val groupConversation3 = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 3L),
            isCreator = false,
            isMember = false,
            groupFlowResultDeferred = { groupModel -> CompletableDeferred(GroupFlowResult.Success(groupModel)) },
        )
        val groupConversation4 = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 4L),
            isCreator = true,
            isMember = true,
            groupFlowResultDeferred = { groupModel -> CompletableDeferred(GroupFlowResult.Success(groupModel)) },
        )
        val groupConversation5 = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 5L),
            isCreator = true,
            isMember = true,
            groupFlowResultDeferred = { CompletableDeferred(GroupFlowResult.Failure.Other) },
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                groupConversation1.id,
                groupConversation2.id,
                groupConversation3.id,
                groupConversation4.id,
                groupConversation5.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    groupConversation1.id to OperationResult.Success,
                    groupConversation2.id to OperationResult.Failure,
                    groupConversation3.id to OperationResult.Success,
                    groupConversation4.id to OperationResult.Success,
                    groupConversation5.id to OperationResult.Failure,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            groupFlowDispatcherMock.runLeaveGroupFlow(
                intent = GroupLeaveIntent.LEAVE_AND_REMOVE,
                groupModel = groupConversation1.groupModel!!,
            )
        }
        verify(exactly = 1) {
            groupFlowDispatcherMock.runLeaveGroupFlow(
                intent = GroupLeaveIntent.LEAVE_AND_REMOVE,
                groupModel = groupConversation2.groupModel!!,
            )
        }
        verify(exactly = 1) {
            groupFlowDispatcherMock.runRemoveGroupFlow(
                groupModel = groupConversation3.groupModel!!,
            )
        }
        verify(exactly = 1) {
            groupFlowDispatcherMock.runDisbandGroupFlow(
                intent = GroupDisbandIntent.DISBAND_AND_REMOVE,
                groupModel = groupConversation4.groupModel!!,
            )
        }
        verify(exactly = 1) {
            groupFlowDispatcherMock.runDisbandGroupFlow(
                intent = GroupDisbandIntent.DISBAND_AND_REMOVE,
                groupModel = groupConversation5.groupModel!!,
            )
        }
        verifyConversationsRefreshed(
            groupConversation1,
            groupConversation2,
            groupConversation3,
            groupConversation4,
            groupConversation5,
        )
        confirmVerified(groupFlowDispatcherMock)
    }

    @Test
    fun `handles group flow dispatcher cancellation`() = runTest {
        // arrange
        val groupConversation = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 1L),
            isCreator = false,
            isMember = true,
            groupFlowResultDeferred = {
                CompletableDeferred<GroupFlowResult>().apply {
                    completeExceptionally(
                        exception = CancellationException("Just a test cancellation"),
                    )
                }
            },
        )
        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                groupConversation.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    groupConversation.id to OperationResult.Failure,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            groupFlowDispatcherMock.runLeaveGroupFlow(
                intent = GroupLeaveIntent.LEAVE_AND_REMOVE,
                groupModel = groupConversation.groupModel!!,
            )
        }
        verifyConversationsRefreshed(groupConversation)
        confirmVerified(groupFlowDispatcherMock)
    }

    @Test
    fun `handles group flow general exception`() = runTest {
        // arrange
        val groupConversation = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 1L),
            isCreator = true,
            isMember = true,
            groupFlowResultDeferred = {
                CompletableDeferred<GroupFlowResult>().apply {
                    completeExceptionally(
                        exception = IllegalStateException("Just an illegal test state"),
                    )
                }
            },
        )
        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                groupConversation.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    groupConversation.id to OperationResult.Failure,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            groupFlowDispatcherMock.runDisbandGroupFlow(
                intent = GroupDisbandIntent.DISBAND_AND_REMOVE,
                groupModel = groupConversation.groupModel!!,
            )
        }
        verifyConversationsRefreshed(groupConversation)
        confirmVerified(groupFlowDispatcherMock)
    }

    @Test
    fun `deletes a single distribution list conversation with success`() = runTest {
        // arrange
        val distributionListConversation = createAndMockDistributionListConversationForDelete(
            distributionListConversationId = DistributionListConversationId(distributionListId = 1L),
            success = true,
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                distributionListConversation.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    distributionListConversation.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            conversationServiceMock.empty(distributionListConversation.distributionList!!)
        }
        verify(exactly = 1) {
            distributionListServiceMock.remove(distributionListConversation.distributionList!!)
        }
        verifyConversationsRefreshed(distributionListConversation)
        confirmVerified(distributionListServiceMock)
    }

    @Test
    fun `deletes a single distribution list conversation without success`() = runTest {
        // arrange
        val distributionListConversation = createAndMockDistributionListConversationForDelete(
            distributionListConversationId = DistributionListConversationId(distributionListId = 1L),
            success = false,
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                distributionListConversation.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    distributionListConversation.id to OperationResult.Failure,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            conversationServiceMock.empty(distributionListConversation.distributionList!!)
        }
        verify(exactly = 1) {
            distributionListServiceMock.remove(distributionListConversation.distributionList!!)
        }
        verifyConversationsRefreshed(distributionListConversation)
        confirmVerified(distributionListServiceMock)
    }

    @Test
    fun `deletes multiple distribution list conversations`() = runTest {
        // arrange
        val distributionListConversation1 = createAndMockDistributionListConversationForDelete(
            distributionListConversationId = DistributionListConversationId(distributionListId = 1L),
            success = true,
        )
        val distributionListConversation2 = createAndMockDistributionListConversationForDelete(
            distributionListConversationId = DistributionListConversationId(distributionListId = 2L),
            success = false,
        )
        val distributionListConversation3 = createAndMockDistributionListConversationForDelete(
            distributionListConversationId = DistributionListConversationId(distributionListId = 3L),
            success = true,
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                distributionListConversation1.id,
                distributionListConversation2.id,
                distributionListConversation3.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    distributionListConversation1.id to OperationResult.Success,
                    distributionListConversation2.id to OperationResult.Failure,
                    distributionListConversation3.id to OperationResult.Success,
                ),
            ),
            actual = result,
        )
        verify(exactly = 3) {
            conversationServiceMock.empty(
                /* distributionListModel = */
                match<DistributionListModel> {
                    it in setOf(
                        distributionListConversation1.distributionList!!,
                        distributionListConversation2.distributionList!!,
                        distributionListConversation3.distributionList!!,
                    )
                },
            )
        }
        verify(exactly = 3) {
            distributionListServiceMock.remove(
                /* distributionListModel = */
                match<DistributionListModel> { distributionListModel ->
                    distributionListModel in setOf(
                        distributionListConversation1.distributionList!!,
                        distributionListConversation2.distributionList!!,
                        distributionListConversation3.distributionList!!,
                    )
                },
            )
        }
        verifyConversationsRefreshed(
            distributionListConversation1,
            distributionListConversation2,
            distributionListConversation3,
        )
        confirmVerified(distributionListServiceMock)
    }

    @Test
    fun `deletes multiple different conversation types`() = runTest {
        // arrange
        val contactConversation1 = createAndMockContactConversationForDelete(
            contactConversationId = ContactConversationId(identity = Identities.OTHER_1.value),
        )
        val contactConversation2 = createAndMockContactConversationForDelete(
            contactConversationId = ContactConversationId(identity = Identities.OTHER_2.value),
        )
        val groupConversation1 = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 1L),
            isCreator = false,
            isMember = true,
            groupFlowResultDeferred = { groupModel -> CompletableDeferred(GroupFlowResult.Success(groupModel)) },
        )
        val groupConversation2 = createAndMockGroupConversationForDelete(
            groupConversationId = GroupConversationId(groupDatabaseId = 2L),
            isCreator = false,
            isMember = true,
            groupFlowResultDeferred = { CompletableDeferred(GroupFlowResult.Failure.Network) },
        )
        val distributionListConversation1 = createAndMockDistributionListConversationForDelete(
            distributionListConversationId = DistributionListConversationId(distributionListId = 1L),
            success = true,
        )
        val distributionListConversation2 = createAndMockDistributionListConversationForDelete(
            distributionListConversationId = DistributionListConversationId(distributionListId = 2L),
            success = false,
        )

        val useCase = createUseCase(testDispatcherProvider())

        // act
        val result = useCase.call(
            conversationIds = listOf(
                contactConversation1.id,
                contactConversation2.id,
                groupConversation1.id,
                groupConversation2.id,
                distributionListConversation1.id,
                distributionListConversation2.id,
            ),
            mode = Mode.DELETE,
        )

        // assert
        assertEquals(
            expected = EmptyOrDeleteConversationsUseCase.Result.Completed(
                operationResults = mapOf(
                    contactConversation1.id to OperationResult.Success,
                    contactConversation2.id to OperationResult.Success,
                    groupConversation1.id to OperationResult.Success,
                    groupConversation2.id to OperationResult.Failure,
                    distributionListConversation1.id to OperationResult.Success,
                    distributionListConversation2.id to OperationResult.Failure,
                ),
            ),
            actual = result,
        )
        verify(exactly = 1) {
            conversationServiceMock.delete(
                /* identity = */
                Identities.OTHER_1.value,
            )
        }
        verify(exactly = 1) {
            conversationServiceMock.delete(
                /* identity = */
                Identities.OTHER_2.value,
            )
        }
        verify(exactly = 1) {
            groupFlowDispatcherMock.runLeaveGroupFlow(
                intent = GroupLeaveIntent.LEAVE_AND_REMOVE,
                groupModel = groupConversation1.groupModel!!,
            )
        }
        verify(exactly = 1) {
            groupFlowDispatcherMock.runLeaveGroupFlow(
                intent = GroupLeaveIntent.LEAVE_AND_REMOVE,
                groupModel = groupConversation2.groupModel!!,
            )
        }
        verify(exactly = 1) {
            distributionListServiceMock.remove(distributionListConversation1.distributionList!!)
        }
        verify(exactly = 1) {
            distributionListServiceMock.remove(distributionListConversation2.distributionList!!)
        }
        verifyConversationsRefreshed(
            contactConversation1,
            contactConversation2,
            groupConversation1,
            groupConversation2,
            distributionListConversation1,
            distributionListConversation2,
        )
    }

    private fun createAndMockContactConversationForEmpty(contactConversationId: ContactConversationId): ConversationModel {
        val contactConversation = createContactConversationModel(contactConversationId)
        every { conversationServiceMock.get(contactConversationId) } returns contactConversation
        every {
            conversationServiceMock.empty(
                /* conversation = */
                contactConversation,
                /* silentMessageUpdate = */
                any(),
            )
        } returns 44
        every { conversationServiceMock.refresh(contactConversation.messageReceiver) } returns contactConversation
        return contactConversation
    }

    private fun createAndMockContactConversationForDelete(contactConversationId: ContactConversationId): ConversationModel {
        val contactConversation = createContactConversationModel(contactConversationId)
        every { conversationServiceMock.get(contactConversationId) } returns contactConversation
        every { conversationServiceMock.delete(contactConversationId.identity) } returns 44
        every { conversationServiceMock.refresh(contactConversation.messageReceiver) } returns contactConversation
        return contactConversation
    }

    private fun createAndMockGroupConversationForEmpty(groupConversationId: GroupConversationId): ConversationModel {
        val groupConversation = createGroupConversationModel(groupConversationId)
        every { conversationServiceMock.get(groupConversationId) } returns groupConversation
        every {
            conversationServiceMock.empty(
                /* conversation = */
                groupConversation,
                /* silentMessageUpdate = */
                any(),
            )
        } returns 44
        every { conversationServiceMock.refresh(groupConversation.messageReceiver) } returns groupConversation
        return groupConversation
    }

    private fun createAndMockGroupConversationForDelete(
        groupConversationId: GroupConversationId,
        isCreator: Boolean,
        isMember: Boolean,
        groupFlowResultDeferred: (GroupModel) -> CompletableDeferred<GroupFlowResult>,
    ): ConversationModel {
        val groupConversation = createGroupConversationModel(
            groupDatabaseId = groupConversationId.groupDatabaseId,
            creatorIdentity = if (isCreator) Identities.ME else Identities.OTHER_1,
            userState = if (isMember) UserState.MEMBER else UserState.LEFT,
        )

        every { conversationServiceMock.get(groupConversationId) } returns groupConversation

        if (!isMember) {
            every {
                groupFlowDispatcherMock.runRemoveGroupFlow(groupConversation.groupModel!!)
            } returns groupFlowResultDeferred(groupConversation.groupModel!!)
        } else if (isCreator) {
            every {
                groupFlowDispatcherMock.runDisbandGroupFlow(
                    intent = any(),
                    groupModel = groupConversation.groupModel!!,
                )
            } returns groupFlowResultDeferred(groupConversation.groupModel!!)
        } else {
            every {
                groupFlowDispatcherMock.runLeaveGroupFlow(
                    intent = any(),
                    groupModel = groupConversation.groupModel!!,
                )
            } returns groupFlowResultDeferred(groupConversation.groupModel!!)
        }

        every { conversationServiceMock.refresh(groupConversation.messageReceiver) } returns groupConversation
        return groupConversation
    }

    private fun createAndMockDistributionListConversationForEmpty(distributionListConversationId: DistributionListConversationId): ConversationModel {
        val distributionListConversation = createDistributionListConversationModel(distributionListConversationId)
        every { conversationServiceMock.get(distributionListConversationId) } returns distributionListConversation
        every {
            conversationServiceMock.empty(
                /* conversation = */
                distributionListConversation,
                /* silentMessageUpdate = */
                any(),
            )
        } returns 44
        every { conversationServiceMock.refresh(distributionListConversation.messageReceiver) } returns distributionListConversation
        return distributionListConversation
    }

    private fun createAndMockDistributionListConversationForDelete(
        distributionListConversationId: DistributionListConversationId,
        success: Boolean,
    ): ConversationModel {
        val distributionListConversation = createDistributionListConversationModel(distributionListConversationId)
        every { conversationServiceMock.get(distributionListConversationId) } returns distributionListConversation
        every { conversationServiceMock.empty(distributionListConversation.distributionList!!) } returns 44
        every { distributionListServiceMock.remove(distributionListConversation.distributionList!!) } returns success
        every { conversationServiceMock.refresh(distributionListConversation.messageReceiver) } returns distributionListConversation
        return distributionListConversation
    }

    private fun verifyConversationsRefreshed(vararg conversationModels: ConversationModel) {
        conversationModels.forEach {
            verify(exactly = 1) {
                conversationServiceMock.refresh(
                    /* receiverModel = */
                    it.messageReceiver,
                )
            }
        }
    }
}
