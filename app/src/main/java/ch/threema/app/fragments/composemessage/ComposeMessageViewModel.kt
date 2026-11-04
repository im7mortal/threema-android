package ch.threema.app.fragments.composemessage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.groupflows.ReportGroupIntent
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.protocolsteps.GroupSpamPotentialDetectionSteps
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.MessageService
import ch.threema.app.utils.SingleLiveEvent
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ComposeMessageViewModel")

class ComposeMessageViewModel(
    private val messageService: MessageService,
    private val dispatcherProvider: DispatcherProvider,
    private val contactModelRepository: ContactModelRepository,
    private val groupSpamPotentialDetectionSteps: GroupSpamPotentialDetectionSteps,
    private val globalEventBuses: GlobalEventBuses,
    private val groupFlowDispatcher: GroupFlowDispatcher,
) : ViewModel() {

    private val _events = SingleLiveEvent<ComposeMessageEvent>()

    @JvmField
    val events: LiveData<ComposeMessageEvent> = _events

    private val _contactAvailabilityStatus = MutableLiveData<AvailabilityStatus>(AvailabilityStatus.None)

    @JvmField
    val contactAvailabilityStatus: LiveData<AvailabilityStatus> = _contactAvailabilityStatus

    private val _showSpamGroupBanner = MutableLiveData(false)

    @JvmField
    val showSpamGroupBanner: LiveData<Boolean> = _showSpamGroupBanner

    private val _reportingGroupInProgress = MutableLiveData(false)

    @JvmField
    val reportingGroupInProgress: LiveData<Boolean> = _reportingGroupInProgress

    private var checkForPotentialSpamGroupJob: Job? = null
    private var getContactAvailabilityStatusJob: Job? = null
    private var reportGroupJob: Job? = null

    fun onResume(messageReceiver: MessageReceiver<*>) {
        if (!BuildFlavor.IS_ON_PREM) {
            refreshSpamGroupBanner(messageReceiver)
        }
        if (BuildConfig.AVAILABILITY_STATUS_ENABLED) {
            refreshAvailabilityStatus(messageReceiver)
        }
    }

    private fun refreshSpamGroupBanner(messageReceiver: MessageReceiver<*>) {
        // Cancel work and reset the spam group banner state first
        checkForPotentialSpamGroupJob?.cancel()
        _showSpamGroupBanner.value = false
        // Run the spam detection steps if this is a group conversation
        if (messageReceiver is GroupMessageReceiver) {
            checkForPotentialSpamGroup(messageReceiver.groupModel)
        }
    }

    private fun checkForPotentialSpamGroup(group: GroupModel?) {
        if (group == null) {
            logger.error("Missing group model to check for spam group")
            return
        }
        checkForPotentialSpamGroupJob = viewModelScope.launch {
            val isPossibleSpamGroup = groupSpamPotentialDetectionSteps.run(group)
            _showSpamGroupBanner.value = isPossibleSpamGroup
            if (isPossibleSpamGroup) {
                // Listen for any outgoing content message in this group conversation
                val groupDatabaseId = withContext(dispatcherProvider.io) { group.getDatabaseId() }
                globalEventBuses.messages
                    .filterIsInstance<MessageEvent.NewMessage>()
                    .first { messageShouldHideSpamGroupBanner(groupDatabaseId, it.message) }
                _showSpamGroupBanner.value = false
            }
        }
    }

    /**
     * If the user sees the potential spam group banner but sends a message in this group conversation,
     * the banner should hide automatically.
     */
    private fun messageShouldHideSpamGroupBanner(
        groupDatabaseId: GroupDatabaseId,
        message: MessageModel,
    ): Boolean {
        // TODO(ANDR-5350): Maybe use the new message property "Persistent Entry"
        return message is GroupMessageModel &&
            message.groupId.toLong() == groupDatabaseId &&
            message.isOutbox &&
            !message.isStatusMessage
    }

    private fun refreshAvailabilityStatus(messageReceiver: MessageReceiver<*>) {
        getContactAvailabilityStatusJob?.cancel()
        if (messageReceiver is ContactMessageReceiver) {
            getContactAvailabilityStatusJob = viewModelScope.launch(dispatcherProvider.io) {
                val availabilityStatus = contactModelRepository.getByIdentity(messageReceiver.contact.identity)
                    ?.data
                    ?.availabilityStatus
                if (isActive) {
                    _contactAvailabilityStatus.postValue(
                        availabilityStatus ?: AvailabilityStatus.None,
                    )
                }
            }
        } else {
            _contactAvailabilityStatus.value = AvailabilityStatus.None
        }
    }

    fun loadNextRecords(
        messageReceiver: MessageReceiver<*>,
        filter: MessageService.MessageFilter,
    ) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                val messageModels = messageService.getMessagesForReceiver(messageReceiver, filter)
                _events.postValue(
                    ComposeMessageEvent.NextRecordsLoaded(
                        messageModels = messageModels,
                        hasMoreRecords = messageModels.size >= filter.pageSize,
                    ),
                )
            }
        }
    }

    fun onClickNoSpamGroup(messageReceiver: MessageReceiver<*>?) {
        val groupModel = (messageReceiver as? GroupMessageReceiver)?.groupModel
        if (groupModel == null) {
            logger.error("Missing group model to mark group as not-spam")
            return
        }
        checkForPotentialSpamGroupJob?.cancel()
        _showSpamGroupBanner.value = false
        viewModelScope.launch(dispatcherProvider.io) {
            // TODO(PRD-1007): Reflect the update spam reporting state of the group
            groupModel.persistSpamReportingState(
                spamReportingState = ConversationSpamReportingState.NOT_SPAM,
            )
        }
    }

    fun onClickReportSpamGroup(
        groupModel: GroupModel,
        alsoBlockCreatorAndRemoveGroup: Boolean,
    ) {
        if (reportGroupJob?.isActive == true) {
            return
        }
        checkForPotentialSpamGroupJob?.cancel()
        reportGroupJob = viewModelScope.launch {
            _reportingGroupInProgress.value = true
            _showSpamGroupBanner.value = false
            val reportGroupIntent =
                if (alsoBlockCreatorAndRemoveGroup) {
                    ReportGroupIntent.REPORT_GROUP_BLOCK_CREATOR_DELETE_GROUP
                } else {
                    ReportGroupIntent.REPORT_GROUP
                }
            val reportGroupResult = groupFlowDispatcher
                .runReportGroupFlow(
                    groupModel = groupModel,
                    intent = reportGroupIntent,
                ).await()
            _reportingGroupInProgress.value = false
            when (reportGroupResult) {
                is GroupFlowResult.Success -> {
                    // Nothing to do
                }
                is GroupFlowResult.Failure -> {
                    _events.value = ComposeMessageEvent.ReportingGroupFailed
                    _showSpamGroupBanner.value = groupSpamPotentialDetectionSteps.run(groupModel)
                }
            }
        }
    }
}
