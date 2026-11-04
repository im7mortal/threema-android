package ch.threema.app.archive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.R
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.drafts.DraftManager
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.typingindicator.TypingIndicatorProvider
import ch.threema.app.usecases.availabilitystatus.WatchAllContactAvailabilityStatusesUseCase
import ch.threema.app.usecases.avatar.GetAndPrepareAvatarUseCase
import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase
import ch.threema.app.usecases.conversations.WatchArchivedConversationListItemsUseCase
import ch.threema.app.usecases.conversations.WatchArchivedConversationsUseCase
import ch.threema.app.usecases.conversations.WatchAvatarIterationsUseCase
import ch.threema.app.usecases.groups.WatchGroupCallsUseCase
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.takeUnlessEmpty
import ch.threema.common.toggle
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.group.GroupModelOld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ArchiveViewModel")

class ArchiveViewModel(
    private val appContext: Context,
    private val conversationService: ConversationService,
    private val contactService: ContactService,
    private val groupService: GroupService,
    private val distributionListService: DistributionListService,
    private val ringtoneService: RingtoneService,
    private val draftManager: DraftManager,
    private val globalEventBuses: GlobalEventBuses,
    private val watchArchivedConversationsUseCase: WatchArchivedConversationsUseCase,
    private val watchGroupCallsUseCase: WatchGroupCallsUseCase,
    private val typingIndicatorProvider: TypingIndicatorProvider,
    private val getAndPrepareAvatarUseCase: GetAndPrepareAvatarUseCase,
    preferenceService: PreferenceService,
    private val emptyOrDeleteConversationsUseCase: EmptyOrDeleteConversationsUseCase,
    private val watchAvatarIterationsUseCase: WatchAvatarIterationsUseCase,
    private val watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    private val watchAllMentionNamesUseCase: WatchAllMentionNamesUseCase,
    private val watchAllContactAvailabilityStatusesUseCase: WatchAllContactAvailabilityStatusesUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val watchArchivedConversationListItemsUseCase = WatchArchivedConversationListItemsUseCase(
        watchArchivedConversationsUseCase = watchArchivedConversationsUseCase,
        watchGroupCallsUseCase = watchGroupCallsUseCase,
        typingIndicatorProvider = typingIndicatorProvider,
        contactService = contactService,
        groupService = groupService,
        distributionListService = distributionListService,
        ringtoneService = ringtoneService,
        watchAvatarIterationsUseCase = watchAvatarIterationsUseCase,
        watchContactNameFormatSettingUseCase = watchContactNameFormatSettingUseCase,
        watchAllMentionNamesUseCase = watchAllMentionNamesUseCase,
        watchAllContactAvailabilityStatusesUseCase = watchAllContactAvailabilityStatusesUseCase,
        draftManager = draftManager,
        timeProvider = timeProvider,
    )

    private val conversationUiModels: Flow<List<ConversationUiModel>> = watchArchivedConversationListItemsUseCase.call()
    private val arePrivateChatsHidden: Flow<Boolean> = preferenceService.watchArePrivateChatsHidden()
    private val selectedConversationIds = MutableStateFlow<Set<ConversationId>>(emptySet())
    private val searchQuery = MutableStateFlow<String?>(null)

    val conversationListItemUiModels: StateFlow<List<ConversationListItemUiModel>> = combine(
        flow = conversationUiModels,
        flow2 = selectedConversationIds,
        flow3 = searchQuery,
        flow4 = arePrivateChatsHidden,
    ) { conversationUiModels, selectedConversationIds, searchQuery, arePrivateChatsHidden ->
        conversationUiModels
            .filter { conversationUiModel ->
                !conversationUiModel.isPrivate || !arePrivateChatsHidden
            }
            .filter { conversationModel ->
                conversationModel.matchesSearchQuery(searchQuery)
            }
            .map { conversationUiModel ->
                ConversationListItemUiModel(
                    model = conversationUiModel,
                    isChecked = selectedConversationIds.contains(conversationUiModel.conversationId),
                    isHighlighted = false,
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList(),
    )

    val contactNameFormat: StateFlow<ContactNameFormat> = watchContactNameFormatSettingUseCase
        .call()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = ContactNameFormat.DEFAULT,
        )

    val selectedCount: Int
        get() = selectedConversationIds.value.size

    private val currentlySelectedConversations: List<ConversationUiModel>
        get() = conversationListItemUiModels
            .value
            .filter(ConversationListItemUiModel::isChecked)
            .map(ConversationListItemUiModel::model)

    private val _deletionInProgress = MutableStateFlow(false)
    val deletionInProgress = _deletionInProgress.asStateFlow()

    private val _events: MutableSharedFlow<ArchiveScreenEvent> = MutableSharedFlow()
    val events = _events.asSharedFlow()

    /**
     *  @return True if at least one conversation is selected **after** this toggle action.
     */
    fun toggleSelected(conversationId: ConversationId): Boolean {
        selectedConversationIds.update { it.toggle(conversationId) }
        return selectedConversationIds.value.isNotEmpty()
    }

    /**
     *  @return True if at least one conversation is selected
     */
    fun selectAll(): Boolean {
        selectedConversationIds.value = conversationListItemUiModels
            .value
            .map { conversationListItemUiModel -> conversationListItemUiModel.model.conversationId }
            .toSet()
        return selectedConversationIds.value.isNotEmpty()
    }

    fun deselectAll() {
        selectedConversationIds.value = emptySet()
    }

    fun setSearchQuery(query: String?) {
        searchQuery.update {
            query?.trim()?.takeUnlessEmpty()
        }
    }

    fun unarchiveAllSelected() {
        val selectedConversationUiModels: List<ConversationUiModel> = currentlySelectedConversations
        viewModelScope.launch {
            if (selectedConversationUiModels.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    val conversationIds: List<ConversationId> = selectedConversationUiModels.map(
                        ConversationUiModel::conversationId,
                    )
                    conversationService.unarchiveByConversationIds(conversationIds, TriggerSource.LOCAL)
                }
            }
            _events.emit(ArchiveScreenEvent.ConversationsUnarchived)
        }
    }

    fun onClickDeleteAllSelected() {
        viewModelScope.launch {
            val selectedConversationUiModels: List<ConversationUiModel> = currentlySelectedConversations
            if (selectedConversationUiModels.isEmpty()) {
                _events.emit(ArchiveScreenEvent.ConversationsDeleted(0))
                return@launch
            }
            var title: String = appContext.resources.getString(
                when {
                    selectedConversationUiModels.size > 1 -> R.string.really_delete_multiple_threads
                    else -> R.string.really_delete_thread
                },
            )
            var message: String = appContext.resources.getQuantityString(
                R.plurals.really_delete_thread_message,
                selectedConversationUiModels.size,
                selectedConversationUiModels.size,
            ) + " " + appContext.getString(R.string.messages_cannot_be_recovered)

            val singleGroupConversationId: GroupConversationId? =
                (selectedConversationUiModels.singleOrNull() as? ConversationUiModel.GroupConversation)?.conversationId
            if (singleGroupConversationId != null) {
                // If only one conversation is deleted, and it's a group, show a more specific message.
                val groupModel: GroupModelOld? = groupService.getById(singleGroupConversationId.groupDatabaseId)
                if (groupModel != null && groupService.isGroupMember(groupModel)) {
                    title = appContext.getString((R.string.action_delete_group))
                    message = if (groupService.isGroupCreator(groupModel)) {
                        appContext.getString(R.string.delete_my_group_message)
                    } else {
                        appContext.getString(R.string.delete_group_message)
                    }
                }
            } else if (selectedConversationUiModels.any { it is ConversationUiModel.GroupConversation }) {
                // If multiple conversations are deleted and at least one of them is a group,
                // show a hint about the leave/dissolve behavior.
                message += " " + appContext.getString(R.string.groups_left_or_dissolved)
            }

            _events.emit(
                ArchiveScreenEvent.ShowReallyDeleteConversationsDialog(
                    ReallyDeleteConversationsDialogContent(
                        title = title,
                        message = message,
                    ),
                ),
            )
        }
    }

    fun confirmDeleteCurrentlySelected() {
        viewModelScope.launch {
            _deletionInProgress.value = true
            try {
                val conversationIds = currentlySelectedConversations.map(ConversationUiModel::conversationId)
                val result = emptyOrDeleteConversationsUseCase.call(
                    conversationIds = conversationIds,
                    mode = EmptyOrDeleteConversationsUseCase.Mode.DELETE,
                )
                when (result) {
                    is EmptyOrDeleteConversationsUseCase.Result.Completed -> {
                        // TODO(ANDR-4175): Remove this event bus call when the conversation cache also holds archived conversations and calls ConversationListener.onRemoved correctly
                        globalEventBuses.conversations.emit(ConversationEvent.AllConversationsUpdated)
                        _events.emit(ArchiveScreenEvent.ConversationsDeleted(result.successCount))
                    }
                    EmptyOrDeleteConversationsUseCase.Result.UnknownConversation -> {
                        logger.warn("Conversation(s) to delete not found")
                    }
                }
            } finally {
                _deletionInProgress.value = false
            }
        }
    }

    suspend fun provideAvatarBitmap(conversationId: ConversationId) =
        getAndPrepareAvatarUseCase.call(conversationId)
}
