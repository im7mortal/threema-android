package ch.threema.app.conversations

import android.content.Context
import androidx.lifecycle.viewModelScope
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ActionEvent
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.DistributionListEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.files.deleteSecurely
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.groupflows.GroupDisbandIntent
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.groupflows.GroupLeaveIntent
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.MessageService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.usecases.avatar.GetAndPrepareAvatarUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.conversation.ExportConversationToFileUseCase
import ch.threema.app.usecases.conversation.SetConversationCategoryUseCase
import ch.threema.app.usecases.conversations.CountArchivedConversationsUseCase
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase
import ch.threema.app.usecases.conversations.WatchUnarchivedConversationListItemsUseCase
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.onCompleted
import ch.threema.common.DispatcherProvider
import ch.threema.common.combine
import ch.threema.common.debounceAfterFirst
import ch.threema.common.stateFlowOf
import ch.threema.common.takeUnlessEmpty
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.ContactModel
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.ConversationTag
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.InjectedParam
import org.slf4j.Logger

private val logger: Logger = getThreemaLogger("ConversationsViewModel")

/**
 *  @param initiallyOpenedConversation If the screen that uses this viewmodel is used in a multi-pane-mode, this value can be passed. The
 *  currently opened conversation is shown as a highlighted list item. It will only be used if [isMultiPaneEnabled] is set to `true`.
 */
class ConversationsViewModel(
    private val applicationContext: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val conversationService: ConversationService,
    private val preferenceService: PreferenceService,
    private val messageService: MessageService,
    private val notificationService: NotificationService,
    private val distributionListService: DistributionListService,
    private val groupModelRepository: GroupModelRepository,
    private val groupFlowDispatcher: GroupFlowDispatcher,
    private val identityProvider: IdentityProvider,
    globalEventFlows: GlobalEventFlows,
    private val globalEventBuses: GlobalEventBuses,
    private val exportConversationToFileUseCase: ExportConversationToFileUseCase,
    private val getAndPrepareAvatarUseCase: GetAndPrepareAvatarUseCase,
    private val watchConversationListItemsUseCase: WatchUnarchivedConversationListItemsUseCase,
    private val watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    private val emptyOrDeleteConversationsUseCase: EmptyOrDeleteConversationsUseCase,
    private val countArchivedConversationsUseCase: CountArchivedConversationsUseCase,
    private val setConversationCategoryUseCase: SetConversationCategoryUseCase,
    @InjectedParam isMultiPaneEnabled: Boolean,
    @InjectedParam initiallyOpenedConversation: ConversationId?,
) : BaseViewModel<ConversationsViewState, ConversationsViewEvent>() {

    private var exportJob: Job? = null

    private val highlightedConversationFlow: StateFlow<ConversationId?> =
        if (isMultiPaneEnabled) {
            globalEventFlows.actions
                .filterIsInstance<ActionEvent.ConversationOpened>()
                .map { it.conversationId }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = initiallyOpenedConversation,
                )
        } else {
            stateFlowOf(null)
        }

    // Not part of the state because the search-bar is an old stateful XML component
    private val searchQuery = MutableStateFlow<String?>(null)

    override suspend fun initialize(): ConversationsViewState {
        runWhenActive {
            produceOngoingState()
        }
        return produceInitialState()
    }

    private suspend fun produceInitialState(): ConversationsViewState = withContext(dispatcherProvider.io) {
        val hidePrivateConversations: Boolean = preferenceService.arePrivateChatsHidden()
        val itemsState: ItemsState = watchConversationUiModelsResult()
            .first()
            .fold(
                onSuccess = { conversationUiModels ->
                    ItemsState.Loaded(
                        items = filterConversationUiModels(
                            conversationUiModels = conversationUiModels,
                            hidePrivateConversations = hidePrivateConversations,
                            searchQuery = null,
                        ).map(::ConversationListItemUiModel),
                    )
                },
                onFailure = {
                    ItemsState.Failed
                },
            )
        val archivedConversationsCount: Long = countArchivedConversationsUseCase.call(
            excludePrivate = hidePrivateConversations,
        )
        val contactNameFormatOrDefault =
            runCatching {
                preferenceService.getContactNameFormat()
            }.getOrElse { throwable ->
                logger.error("Failed to read contact name format setting from preferences", throwable)
                ContactNameFormat.DEFAULT
            }
        ConversationsViewState(
            itemsState = itemsState,
            hidePrivateConversations = hidePrivateConversations,
            hasPrivateConversations = conversationService.hasAnyPrivateConversations(false),
            archivedConversationsCount = archivedConversationsCount,
            contactNameFormat = contactNameFormatOrDefault,
            availabilityStatus = getAvailabilityStatusOrNone(),
            pendingAction = null,
        )
    }

    private suspend fun produceOngoingState() {
        combine(
            flow = watchConversationUiModelsResult(),
            flow2 = preferenceService.watchArePrivateChatsHidden(),
            flow3 = highlightedConversationFlow,
            flow4 = watchContactNameFormatSettingUseCase.call(),
            flow5 = watchAvailabilityStatusOrNone(),
            flow6 = watchSearchQueryDebounced(),
        ) { conversationUiModelsResult, hidePrivateConversations, openedConversationId, contactNameFormat, availabilityStatus, searchQuery: String? ->
            val updatedItemsState = conversationUiModelsResult
                .fold(
                    onSuccess = { conversationUiModels ->
                        ItemsState.Loaded(
                            items = filterConversationUiModels(
                                conversationUiModels = conversationUiModels,
                                hidePrivateConversations = hidePrivateConversations,
                                searchQuery = searchQuery,
                            ).map { conversationUiModel ->
                                ConversationListItemUiModel(
                                    model = conversationUiModel,
                                    isHighlighted = conversationUiModel.conversationId == openedConversationId,
                                )
                            },
                        )
                    },
                    onFailure = {
                        ItemsState.Failed
                    },
                )
            OngoingConversationsViewState(
                itemsState = updatedItemsState,
                hidePrivateConversations = hidePrivateConversations,
                hasPrivateConversations = conversationService.hasAnyPrivateConversations(false),
                contactNameFormat = contactNameFormat,
                availabilityStatus = availabilityStatus,
                searchQuery = searchQuery,
            )
        }
            .flowOn(dispatcherProvider.worker)
            .collectLatest { ongoingConversationsViewState ->
                updateViewState {
                    copy(
                        itemsState = ongoingConversationsViewState.itemsState,
                        hidePrivateConversations = ongoingConversationsViewState.hidePrivateConversations,
                        hasPrivateConversations = ongoingConversationsViewState.hasPrivateConversations,
                        contactNameFormat = ongoingConversationsViewState.contactNameFormat,
                        availabilityStatus = ongoingConversationsViewState.availabilityStatus,
                    )
                }
                val archivedConversationsCount: Long = countArchivedConversationsUseCase.call(
                    filterQuery = ongoingConversationsViewState.searchQuery,
                    excludePrivate = ongoingConversationsViewState.hidePrivateConversations,
                )
                currentCoroutineContext().ensureActive()
                updateViewState {
                    copy(
                        archivedConversationsCount = archivedConversationsCount,
                    )
                }
            }
    }

    private fun watchConversationUiModelsResult(): Flow<Result<List<ConversationUiModel>>> =
        watchConversationListItemsUseCase
            .call()
            .map { conversationUiModels ->
                Result.success(conversationUiModels)
            }
            .catch { throwable ->
                logger.error("Failed to load conversation ui models", throwable)
                emit(Result.failure(throwable))
            }

    private fun getAvailabilityStatusOrNone(): AvailabilityStatus =
        runCatching {
            preferenceService.getAvailabilityStatus()
                ?: AvailabilityStatus.None
        }.getOrElse { throwable ->
            logger.error("Failed to users availability status from preferences", throwable)
            AvailabilityStatus.None
        }

    private fun watchAvailabilityStatusOrNone(): StateFlow<AvailabilityStatus> =
        preferenceService.watchAvailabilityStatus()
            .map { availabilityStatus ->
                availabilityStatus ?: AvailabilityStatus.None
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = getAvailabilityStatusOrNone(),
            )

    /**
     *  The initial value of [searchQuery] will be emitted directly.
     *  All following values will be debounced.
     */
    @OptIn(FlowPreview::class)
    private fun watchSearchQueryDebounced() =
        searchQuery
            .debounceAfterFirst(300.milliseconds)

    fun onLongClickConversationItem(conversationId: ConversationId) = runAction {
        val conversationModel: ConversationModel? = conversationService.get(conversationId)
        if (conversationModel != null) {
            emitEvent(ConversationsViewEvent.OpenConversationActionDialog(conversationModel))
        } else {
            logger.warn("Could not show conversation action dialog because conversation model is missing for receiver {}", conversationId)
            emitEvent(ConversationsViewEvent.InternalError)
        }
    }

    fun onClickOpenArchive() = runAction {
        emitEvent(ConversationsViewEvent.OpenArchive(searchQuery.value))
    }

    fun onSearchQueryChange(searchQuery: String?) {
        this.searchQuery.value = searchQuery?.takeUnlessEmpty()
    }

    /**
     *  Takes a list of all conversation models and returns a list of conversation models that should be effectively presented to the user.
     */
    private fun filterConversationUiModels(
        conversationUiModels: List<ConversationUiModel>,
        hidePrivateConversations: Boolean,
        searchQuery: String?,
    ): List<ConversationUiModel> =
        conversationUiModels
            .filter { conversationUiModel ->
                !conversationUiModel.isPrivate || !hidePrivateConversations
            }
            .filter { conversationUiModel ->
                conversationUiModel.matchesSearchQuery(searchQuery)
            }

    /**
     *  @param isAndroidSystemLockConfigured Whether the system has a screen lock defined (not Threema)
     */
    fun onViewResumed(isAndroidSystemLockConfigured: Boolean) = runAction {
        // Show private hidden chats if the lock machinism was SYSTEM, and it was removed by the user
        if (preferenceService.getLockMechanism() == PreferenceService.LockMechanism.SYSTEM && !isAndroidSystemLockConfigured) {
            emitEvent(ConversationsViewEvent.OnSystemLockWasRemoved)
            with(preferenceService) {
                setLockMechanism(PreferenceService.LockMechanism.NONE)
                setAppLockEnabled(false)
                setArePrivateChatsHidden(false)
            }
            emitEvent(ConversationsViewEvent.UpdateWidgets)
            firePrivateReceiverUpdate()
        }
    }

    fun onClickMarkConversationAsPrivate(conversationId: ConversationId) = runAction {
        val conversationModel = conversationService.getCached(conversationId)
        if (conversationModel == null) {
            logger.error("Could not mark conversation as private because the model is missing")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }
        if (!preferenceService.hasLockMechanism()) {
            updateViewState {
                copy(
                    pendingAction = ConversationsViewPendingAction.MarkConversationAsPrivate(conversationModel),
                )
            }
            emitEvent(
                event = ConversationsViewEvent.LockMechanismRequiredToUpdatePrivateConversationMark(
                    targetValueIsMarkedAsPrivate = true,
                ),
            )
            return@runAction
        }

        if (conversationModel.conversationCategory == ConversationCategory.PROTECTED) {
            logger.warn("Could not mark the conversation as private because it is already private")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }

        emitEvent(
            event = ConversationsViewEvent.ConfirmationRequiredToMarkConversationAsPrivate(conversationId),
        )
    }

    fun onLockMechanismConfiguredToMarkConversationAsPrivate() = runAction {
        if (!preferenceService.hasLockMechanism()) {
            return@runAction
        }
        val conversationModel = (currentViewState.pendingAction as? ConversationsViewPendingAction.MarkConversationAsPrivate)?.conversation
        if (conversationModel == null) {
            logger.error("Could not effectively mark the conversation as private because there was no pending action for it")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }
        completePendingAction()
        markConversationAsPrivateEffectively(conversationModel)
    }

    fun onClickConfirmMarkConversationAsPrivate(conversationId: ConversationId) = runAction {
        val conversationModel: ConversationModel? = conversationService.getCached(conversationId)
        if (conversationModel != null) {
            markConversationAsPrivateEffectively(conversationModel)
        } else {
            logger.error("Could not effectively mark the conversation as private because the model is missing")
            emitEvent(ConversationsViewEvent.InternalError)
        }
    }

    private fun markConversationAsPrivateEffectively(conversationModel: ConversationModel) = runAction {
        if (!preferenceService.hasLockMechanism()) {
            logger.warn("Can not mark a conversation as private without a configured lock mechanism")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }
        setConversationCategoryUseCase.call(
            conversationId = conversationModel.id,
            conversationCategory = ConversationCategory.PROTECTED,
            triggerSource = TriggerSource.LOCAL,
        )
        emitEvent(ConversationsViewEvent.ConversationMarkAsPrivateSuccess)
        fireReceiverUpdate(conversationModel.messageReceiver)
        if (preferenceService.arePrivateChatsHidden()) {
            firePrivateReceiverUpdate()
        }
    }

    fun onClickUnmarkConversationAsPrivate(conversationId: ConversationId) = runAction {
        val conversationModel = conversationService.getCached(conversationId)
        if (conversationModel == null) {
            logger.error("Could not unmark conversation as private because the model is missing")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }
        if (!preferenceService.hasLockMechanism()) {
            updateViewState {
                copy(
                    pendingAction = ConversationsViewPendingAction.UnmarkConversationAsPrivate(conversationModel),
                )
            }
            emitEvent(
                event = ConversationsViewEvent.LockMechanismRequiredToUpdatePrivateConversationMark(
                    targetValueIsMarkedAsPrivate = false,
                ),
            )
            return@runAction
        }

        if (conversationModel.conversationCategory != ConversationCategory.PROTECTED) {
            logger.warn("Could not unmark the conversation as private because it is not private")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }

        updateViewState {
            copy(
                pendingAction = ConversationsViewPendingAction.UnmarkConversationAsPrivate(conversationModel),
            )
        }
        emitEvent(ConversationsViewEvent.UnlockRequiredToUnmarkConversationAsPrivate)
    }

    fun onLockMechanismConfiguredToUnmarkConversationAsPrivate() {
        if (preferenceService.hasLockMechanism()) {
            unmarkConversationAsPrivateEffectively()
        }
    }

    fun onUnlockSuccessToUnmarkConversationAsPrivate() {
        unmarkConversationAsPrivateEffectively()
    }

    private fun unmarkConversationAsPrivateEffectively() = runAction {
        val conversationModel = (currentViewState.pendingAction as? ConversationsViewPendingAction.UnmarkConversationAsPrivate)?.conversation
        if (conversationModel == null) {
            logger.error("Could not effectively unmark the conversation as private because there was no pending action for it")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }
        completePendingAction()
        if (!preferenceService.hasLockMechanism()) {
            return@runAction
        }
        setConversationCategoryUseCase.call(
            conversationId = conversationModel.id,
            conversationCategory = ConversationCategory.DEFAULT,
            triggerSource = TriggerSource.LOCAL,
        )
        emitEvent(ConversationsViewEvent.ConversationUnmarkAsPrivateSuccess)
        fireReceiverUpdate(conversationModel.messageReceiver)
    }

    fun onClickHideOrShowPrivateConversations() = runAction {
        val arePrivateChatsCurrentlyHidden = preferenceService.arePrivateChatsHidden()

        // This state can only be reached by setting the SYSTEM lock mechanism in Threema and then removing it in system settings
        if (!arePrivateChatsCurrentlyHidden && !preferenceService.hasLockMechanism()) {
            emitEvent(ConversationsViewEvent.LockMechanismRequiredToHidePrivateConversations)
            return@runAction
        }

        if (arePrivateChatsCurrentlyHidden) {
            emitEvent(ConversationsViewEvent.UnlockRequiredToShowPrivateConversations)
            return@runAction
        }

        preferenceService.setArePrivateChatsHidden(true)
        emitEvent(ConversationsViewEvent.UpdateWidgets)
        firePrivateReceiverUpdate()
    }

    fun onLockMechanismConfiguredToHidePrivateConversations() = runAction {
        if (preferenceService.hasLockMechanism()) {
            preferenceService.setArePrivateChatsHidden(true)
            emitEvent(ConversationsViewEvent.UpdateWidgets)
            firePrivateReceiverUpdate()
        }
    }

    fun onUnlockSuccessToShowPrivateConversations() = runAction {
        if (!preferenceService.arePrivateChatsHidden()) {
            // The setting changed in the meantime, nothing to do
            return@runAction
        }
        preferenceService.setArePrivateChatsHidden(false)
        emitEvent(ConversationsViewEvent.UpdateWidgets)
        firePrivateReceiverUpdate()
    }

    fun onSwipedListItemPin(conversationId: ConversationId) {
        togglePinConversation(conversationId)
    }

    fun onSwipedListItemArchive(conversationId: ConversationId) {
        archiveConversation(conversationId)
    }

    fun onClickArchiveConversation(conversationId: ConversationId) {
        archiveConversation(conversationId)
    }

    private fun archiveConversation(conversationId: ConversationId) = runAction {
        val conversationModel: ConversationModel? = conversationService.getCached(conversationId)
        if (conversationModel != null) {
            conversationService.archive(conversationModel, TriggerSource.LOCAL)
            emitEvent(ConversationsViewEvent.ConversationArchived(conversationModel))
        } else {
            logger.warn("Could not archive conversation because conversation model is missing for conversation {}", conversationId)
            emitEvent(ConversationsViewEvent.InternalError)
        }
    }

    private fun togglePinConversation(conversationId: ConversationId) = runAction {
        val conversationModel = conversationService.getCached(conversationId) ?: run {
            logger.warn("Could not pin/unpin conversation because conversation model is missing for conversation {}", conversationId)
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }
        when {
            conversationModel.isContactConversation -> {
                val contactModel = conversationModel.contactModel
                    ?: return@runAction
                val newConversationVisibility = contactModel.getToggledPinConversationVisibility()
                    ?: return@runAction

                contactModel.setConversationVisibilityFromLocalOrRemote(newConversationVisibility)
                conversationModel.conversationVisibility = newConversationVisibility
            }

            conversationModel.isGroupConversation -> {
                val groupModel = conversationModel.groupModel
                    ?: return@runAction

                val newConversationVisibility = groupModel.getToggledPinConversationVisibility()
                    ?: return@runAction

                groupModel.setConversationVisibilityFromLocalOrRemote(newConversationVisibility)
                conversationModel.conversationVisibility = newConversationVisibility
            }

            conversationModel.isDistributionListConversation -> {
                val distributionListModel = conversationModel.distributionList
                    ?: return@runAction

                val newConversationVisibility = distributionListModel.conversationVisibility.getToggledPinConversationVisibility()
                    ?: return@runAction

                distributionListService.setConversationVisibility(
                    distributionListModel,
                    newConversationVisibility,
                )
                conversationModel.conversationVisibility = newConversationVisibility
            }

            else -> {
                logger.error("Conversation is of unknown type")
            }
        }
    }

    private fun ContactModel.getToggledPinConversationVisibility(): ConversationVisibility? =
        data?.conversationVisibility?.getToggledPinConversationVisibility()

    private fun GroupModel.getToggledPinConversationVisibility(): ConversationVisibility? =
        data?.conversationVisibility?.getToggledPinConversationVisibility()

    private fun ConversationVisibility.getToggledPinConversationVisibility(): ConversationVisibility? = when (this) {
        ConversationVisibility.NORMAL -> ConversationVisibility.PINNED
        ConversationVisibility.ARCHIVED -> null
        ConversationVisibility.PINNED -> ConversationVisibility.NORMAL
    }

    fun onClickMarkConversationAsRead(conversationId: ConversationId) = runAction {
        val conversationModel = conversationService.getCached(conversationId)
        if (conversationModel == null) {
            logger.error("Could not mark conversation as read because the model is missing")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }
        conversationService.untag(conversationModel, ConversationTag.MARKED_AS_UNREAD)
        withContext(dispatcherProvider.io) {
            messageService.markConversationAsRead(conversationModel.messageReceiver, notificationService)
        }
    }

    fun onClickMarkConversationAsUnread(conversationId: ConversationId) = runAction {
        val conversationModel = conversationService.getCached(conversationId)
        if (conversationModel == null) {
            logger.error("Could not mark conversation as unread because the model is missing")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }
        conversationService.tag(conversationModel, ConversationTag.MARKED_AS_UNREAD)
    }

    fun onClickEmptyConversation(conversationId: ConversationId) = runAction {
        emitEvent(
            event = ConversationsViewEvent.ConfirmationRequiredToEmptyConversation(conversationId),
        )
    }

    fun onClickConfirmEmptyConversation(conversationId: ConversationId) {
        emptyConversation(conversationId)
    }

    fun onClickDeleteContactConversation(conversationId: ConversationId) = runAction {
        emitEvent(
            event = ConversationsViewEvent.ConfirmationRequiredToDeleteContactConversation(conversationId),
        )
    }

    fun onClickedConfirmDeleteContactConversation(conversationId: ConversationId) {
        deleteConversation(conversationId)
    }

    fun onClickDeleteDistributionListConversation(conversationId: ConversationId) = runAction {
        emitEvent(
            event = ConversationsViewEvent.ConfirmationRequiredToDeleteDistributionListConversation(
                conversationId = conversationId,
            ),
        )
    }

    fun onClickedConfirmDeleteDistributionListConversation(conversationId: ConversationId) {
        deleteConversation(conversationId)
    }

    suspend fun provideAvatarBitmap(conversationId: ConversationId) =
        getAndPrepareAvatarUseCase.call(conversationId)

    private fun emptyConversation(conversationId: ConversationId) = runAction {
        emitEvent(ConversationsViewEvent.OnEmptyingConversation)
        val result: EmptyOrDeleteConversationsUseCase.Result = emptyOrDeleteConversationsUseCase.call(
            conversationIds = listOf(conversationId),
            mode = EmptyOrDeleteConversationsUseCase.Mode.EMPTY,
        )
        emitEvent(ConversationsViewEvent.OnEmptyingConversationResult(result))
    }

    private fun deleteConversation(conversationId: ConversationId) = runAction {
        emitEvent(ConversationsViewEvent.OnDeletingConversation)
        val result: EmptyOrDeleteConversationsUseCase.Result = emptyOrDeleteConversationsUseCase.call(
            conversationIds = listOf(conversationId),
            mode = EmptyOrDeleteConversationsUseCase.Mode.DELETE,
        )
        emitEvent(ConversationsViewEvent.OnDeletingConversationResult(result))
    }

    private fun fireReceiverUpdate(receiver: MessageReceiver<*>) {
        when (receiver) {
            is GroupMessageReceiver -> {
                val groupIdentity = GroupIdentity(
                    creatorIdentity = receiver.group.creatorIdentity,
                    groupId = receiver.group.apiGroupId.toLong(),
                )
                globalEventBuses.groups.emit(GroupEvent.GroupUpdated(groupIdentity))
            }

            is ContactMessageReceiver -> {
                globalEventBuses.contacts.emit(ContactEvent.ContactUpdated(Identity(receiver.contact.identity)))
            }

            is DistributionListMessageReceiver -> {
                globalEventBuses.distributionLists.emit(
                    DistributionListEvent.DistributionListUpdated(receiver.distributionList),
                )
            }
        }
    }

    private fun firePrivateReceiverUpdate() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // fire an update for every secret receiver (to update webclient data)
                conversationService.getAll(false)
                    .filter { conversationModel ->
                        conversationModel.conversationCategory == ConversationCategory.PROTECTED
                    }
                    .forEach { conversationModel ->
                        fireReceiverUpdate(conversationModel.messageReceiver)
                    }
            }
        }
    }

    fun onMissingPermissionToShareConversation(conversationId: ConversationId) = runAction {
        updateViewState {
            copy(
                pendingAction = ConversationsViewPendingAction.ShareConversation(conversationId),
            )
        }
        emitEvent(
            event = ConversationsViewEvent.StoragePermissionRequiredToShareConversation,
        )
    }

    fun onShareConversation() = runAction {
        val conversationId = (currentViewState.pendingAction as? ConversationsViewPendingAction.ShareConversation)?.conversationId
        if (conversationId == null) {
            logger.error("Failed to share the conversation because there was no pending action for it")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }
        completePendingAction()
        emitEvent(
            event = ConversationsViewEvent.OnShareConversation(conversationId),
        )
    }

    fun shareConversation(conversationId: ConversationId, password: String, includeMedia: Boolean) = runAction {
        val conversationModel: ConversationModel? = conversationService.getCached(conversationId)
        if (conversationModel == null) {
            logger.error("Failed to share conversation because the model is missing")
            emitEvent(ConversationsViewEvent.InternalError)
            return@runAction
        }

        exportJob?.cancel()
        exportJob = launchAction {
            val exportResult: ExportConversationToFileUseCase.Result = exportConversationToFileUseCase.call(
                conversationModel = conversationModel,
                password = password,
                includeMedia = includeMedia,
            )

            when (exportResult) {
                is ExportConversationToFileUseCase.Result.Success -> {
                    updateViewState {
                        copy(
                            pendingAction = ConversationsViewPendingAction.DeleteSharedConversationFile(exportResult.file),
                        )
                    }
                    emitEvent(ConversationsViewEvent.OnConversationFileReadyForSharing(exportResult.file))
                }
                ExportConversationToFileUseCase.Result.Failure -> {
                    emitEvent(ConversationsViewEvent.OnFailedToCreateConversationFileForSharing)
                }
            }
        }
    }

    fun onClickCancelSharing() {
        exportJob?.cancel()
    }

    /**
     *  After the sharing intent is done (the user shared the file, or just canceled it), we have to delete the temporary file.
     *
     *  We cannot delete the file immediately as some apps (e.g. Dropbox) take some time until they read the file after the intent has been completed.
     *  As we can't know for sure when they're done, we simply wait two minutes before we delete the temporary file.
     *
     *  If this deletion fails, the file will still eventually be deleted by [ch.threema.app.files.TempFilesCleanupWorker].
     */
    fun onSharingIntentCompleted() = runAction {
        val conversationFile = (currentViewState.pendingAction as? ConversationsViewPendingAction.DeleteSharedConversationFile?)?.file
        if (conversationFile == null) {
            logger.warn("Could not delete temporary conversation file after sharing intent was done because the pending action is missing")
            return@runAction
        }
        completePendingAction()
        val conversationFilePath: String = conversationFile.absolutePath

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            try {
                delay(2.minutes)
            } finally {
                File(conversationFilePath).deleteSecurely(applicationContext)
            }
        }
    }

    fun leaveGroup(intent: GroupLeaveIntent, groupDatabaseId: GroupDatabaseId?) = runAction {
        if (groupDatabaseId == null) {
            logger.error("Cannot leave group: groupDatabaseId is null")
            emitEvent(ConversationsViewEvent.OnLeaveGroupFailedInternally)
            return@runAction
        }
        val groupModel = groupModelRepository.getByGroupDatabaseId(groupDatabaseId)
        if (groupModel == null) {
            logger.error("Cannot leave group: groupModel is missing")
            emitEvent(ConversationsViewEvent.OnLeaveGroupFailedInternally)
            return@runAction
        }
        emitEvent(
            event = ConversationsViewEvent.OnLeavingGroup,
        )
        groupFlowDispatcher
            .runLeaveGroupFlow(
                intent = intent,
                groupModel = groupModel,
            )
            .onCompleted(
                onCompletedExceptionally = { exception: Throwable? ->
                    logger.error("leave-group-flow was completed exceptionally", exception)
                    runAction {
                        emitEvent(
                            event = ConversationsViewEvent.OnLeaveGroupCompleted(
                                result = GroupFlowResult.Failure.Other,
                            ),
                        )
                    }
                },
                onCompletedNormally = { result: GroupFlowResult? ->
                    if (result == null) {
                        logger.error("leave-group-flow was completed without a result")
                    }
                    runAction {
                        emitEvent(
                            event = if (result != null) {
                                ConversationsViewEvent.OnLeaveGroupCompleted(result)
                            } else {
                                ConversationsViewEvent.OnLeaveGroupFailedInternally
                            },
                        )
                    }
                },
            )
    }

    fun disbandGroup(intent: GroupDisbandIntent, groupDatabaseId: GroupDatabaseId?) = runAction {
        if (groupDatabaseId == null) {
            logger.error("Cannot disband group: groupDatabaseId is null")
            emitEvent(ConversationsViewEvent.OnDisbandGroupFailedInternally)
            return@runAction
        }
        val groupModel = groupModelRepository.getByGroupDatabaseId(groupDatabaseId)
        if (groupModel == null) {
            logger.error("Cannot disband group: groupModel is missing")
            emitEvent(ConversationsViewEvent.OnDisbandGroupFailedInternally)
            return@runAction
        }
        emitEvent(
            event = ConversationsViewEvent.OnDisbandingGroup,
        )
        groupFlowDispatcher
            .runDisbandGroupFlow(
                intent = intent,
                groupModel = groupModel,
            ).onCompleted(
                onCompletedExceptionally = { exception: Throwable? ->
                    logger.error("disband-group-flow was completed exceptionally", exception)
                    runAction {
                        emitEvent(
                            event = ConversationsViewEvent.OnDisbandGroupCompleted(
                                result = GroupFlowResult.Failure.Other,
                            ),
                        )
                    }
                },
                onCompletedNormally = { result: GroupFlowResult? ->
                    if (result == null) {
                        logger.error("disband-group-flow was completed without a result")
                    }
                    runAction {
                        emitEvent(
                            event = if (result != null) {
                                ConversationsViewEvent.OnDisbandGroupCompleted(result)
                            } else {
                                ConversationsViewEvent.OnDisbandGroupFailedInternally
                            },
                        )
                    }
                },
            )
    }

    fun removeGroup(groupDatabaseId: GroupDatabaseId?) = runAction {
        if (groupDatabaseId == null) {
            logger.error("Cannot remove group: groupDatabaseId is null")
            emitEvent(ConversationsViewEvent.OnRemoveGroupFailedInternally)
            return@runAction
        }
        val groupModel = groupModelRepository.getByGroupDatabaseId(groupDatabaseId)
            ?: run {
                // Group already removed
                return@runAction
            }
        val myIdentity = identityProvider.getIdentity() ?: run {
            logger.error("Cannot remove group if the identity is null")
            emitEvent(ConversationsViewEvent.OnRemoveGroupFailedInternally)
            return@runAction
        }

        val groupModelData: GroupModelData = groupModel.data
            ?: return@runAction
        if (groupModelData.isMember) {
            // Disband or leave if the user is still part of the group.
            if (groupModelData.groupIdentity.creatorIdentity == myIdentity.value) {
                disbandGroup(
                    intent = GroupDisbandIntent.DISBAND_AND_REMOVE,
                    groupDatabaseId = groupDatabaseId,
                )
            } else {
                leaveGroup(
                    intent = GroupLeaveIntent.LEAVE_AND_REMOVE,
                    groupDatabaseId = groupDatabaseId,
                )
            }
        } else {
            // Just remove the group
            emitEvent(ConversationsViewEvent.OnRemovingGroup)
            groupFlowDispatcher
                .runRemoveGroupFlow(groupModel)
                .onCompleted(
                    onCompletedExceptionally = { exception: Throwable? ->
                        logger.error("remove-group-flow was completed exceptionally", exception)
                        runAction {
                            emitEvent(
                                event = ConversationsViewEvent.OnRemoveGroupFailedInternally,
                            )
                        }
                    },
                    onCompletedNormally = { result: GroupFlowResult? ->
                        if (result == null) {
                            logger.error("remove-group-flow was completed without a result")
                        }
                        runAction {
                            emitEvent(
                                event = if (result != null) {
                                    ConversationsViewEvent.OnRemoveGroupCompleted(result)
                                } else {
                                    ConversationsViewEvent.OnRemoveGroupFailedInternally
                                },
                            )
                        }
                    },
                )
        }
    }

    private suspend fun completePendingAction() {
        updateViewState {
            copy(
                pendingAction = null,
            )
        }
    }

    fun onClickContactSupport() = runAction {
        emitEvent(ConversationsViewEvent.OnContactSupport)
    }

    fun onContactSupportFailed(message: String) = runAction {
        emitEvent(ConversationsViewEvent.OnContactSupportFailed(message))
    }
}

private data class OngoingConversationsViewState(
    val itemsState: ItemsState,
    val hidePrivateConversations: Boolean,
    val hasPrivateConversations: Boolean,
    val contactNameFormat: ContactNameFormat,
    val availabilityStatus: AvailabilityStatus,
    val searchQuery: String?,
)
