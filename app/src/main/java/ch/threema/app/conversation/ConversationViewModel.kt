package ch.threema.app.conversation

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ActionEvent
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.usecases.avatar.GetAndPrepareAvatarUseCase
import ch.threema.app.usecases.conversation.MarkConversationAsReadUseCase
import ch.threema.app.usecases.conversation.ReportConversationShortcutUsedUseCase
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class ConversationViewModel(
    conversationId: ConversationId,
    private val hasInitialFocus: Boolean,
    private val initialText: String,
    private val globalEventBuses: GlobalEventBuses,
    private val conversationCategoryService: ConversationCategoryService,
    private val preferenceService: PreferenceService,
    private val notificationService: NotificationService,
    private val getAndPrepareAvatarUseCase: GetAndPrepareAvatarUseCase,
    private val markConversationAsReadUseCase: MarkConversationAsReadUseCase,
    private val watchConversationContactReceiverStateUseCase: WatchConversationContactReceiverStateUseCase,
    private val watchConversationGroupReceiverStateUseCase: WatchConversationGroupReceiverStateUseCase,
    private val watchConversationDistributionListReceiverStateUseCase: WatchConversationDistributionListReceiverStateUseCase,
    private val reportConversationShortcutUsedUseCase: ReportConversationShortcutUsedUseCase,
) : BaseViewModel<ConversationScreenState, ConversationScreenEvent>() {

    private val conversationId: MutableStateFlow<ConversationId> = MutableStateFlow(conversationId)

    private val currentConversationId: ConversationId
        get() = conversationId.value

    override suspend fun initialize(): ConversationScreenState {
        val initialState = produceInitialState(
            conversationId = currentConversationId,
            hasInitialFocus = hasInitialFocus,
            initialText = initialText,
        )

        if (
            initialState.secureConversationState is SecureConversationState.Private &&
            initialState.secureConversationState.appLockConfigured
        ) {
            emitEvent(ConversationScreenEvent.CheckAppLock)
        }

        if (initialState.secureConversationState is SecureConversationState.NotPrivate) {
            markConversationAsReadUseCase.call(initialState.conversationId)
            emitGlobalEventConversationOpened(initialState.conversationId)
        }

        notificationService.setVisibleConversation(initialState.conversationId)
        reportConversationShortcutUsedUseCase.call(initialState.conversationId)

        if (initialState.receiverState !is ConversationReceiverState.Unknown) {
            produceOngoingState()
        }
        return initialState
    }

    fun switchConversation(
        conversationId: ConversationId,
        hasInitialFocus: Boolean,
        initialText: String,
    ) = runAction {
        val initialState = produceInitialState(
            conversationId = conversationId,
            hasInitialFocus = hasInitialFocus,
            initialText = initialText,
        )
        if (
            initialState.secureConversationState is SecureConversationState.Private &&
            initialState.secureConversationState.appLockConfigured
        ) {
            emitEvent(ConversationScreenEvent.CheckAppLock)
        }

        if (initialState.secureConversationState is SecureConversationState.NotPrivate) {
            markConversationAsReadUseCase.call(initialState.conversationId)
            emitGlobalEventConversationOpened(initialState.conversationId)
        }

        notificationService.setVisibleConversation(conversationId)
        reportConversationShortcutUsedUseCase.call(initialState.conversationId)

        updateViewState { initialState }
        this@ConversationViewModel.conversationId.value = conversationId
    }

    private suspend fun produceInitialState(
        conversationId: ConversationId,
        hasInitialFocus: Boolean,
        initialText: String,
    ): ConversationScreenState {
        val receiverState = getInitialReceiverState(conversationId)
        val secureConversationState =
            if (receiverState is ConversationReceiverState.Unknown) {
                SecureConversationState.NotPrivate
            } else {
                getSecureConversationState(conversationId)
            }
        return ConversationScreenState(
            conversationId = conversationId,
            hasInitialFocus = hasInitialFocus,
            text = initialText,
            receiverState = receiverState,
            secureConversationState = secureConversationState,
        )
    }

    private suspend fun getInitialReceiverState(conversationId: ConversationId): ConversationReceiverState =
        when (conversationId) {
            is ContactConversationId ->
                watchConversationContactReceiverStateUseCase.call(contactConversationId = conversationId).first()
            is GroupConversationId ->
                watchConversationGroupReceiverStateUseCase.call(groupConversationId = conversationId).first()
            is DistributionListConversationId ->
                watchConversationDistributionListReceiverStateUseCase.call(distributionListConversationId = conversationId).first()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun produceOngoingState() = runAction {
        conversationId
            .flatMapLatest { conversationId ->
                watchReceiverState(conversationId)
                    .map { conversationReceiverState ->
                        conversationId to conversationReceiverState
                    }
            }.collect { (conversationId, conversationReceiverState) ->
                updateViewState {
                    copy(
                        conversationId = conversationId,
                        receiverState = conversationReceiverState,
                    )
                }
            }
    }

    private fun watchReceiverState(conversationId: ConversationId): Flow<ConversationReceiverState> =
        when (conversationId) {
            is ContactConversationId ->
                watchConversationContactReceiverStateUseCase.call(contactConversationId = conversationId)
            is GroupConversationId ->
                watchConversationGroupReceiverStateUseCase.call(groupConversationId = conversationId)
            is DistributionListConversationId ->
                watchConversationDistributionListReceiverStateUseCase.call(distributionListConversationId = conversationId)
        }

    private fun getSecureConversationState(conversationId: ConversationId): SecureConversationState =
        conversationCategoryService
            .isMarkedAsPrivate(conversationId)
            .let { isPrivate ->
                if (isPrivate) {
                    SecureConversationState.Private.locked(
                        appLockConfigured = preferenceService.hasLockMechanism(),
                    )
                } else {
                    SecureConversationState.NotPrivate
                }
            }

    private fun emitGlobalEventConversationOpened(conversationId: ConversationId) {
        globalEventBuses.actions.emit(
            event = ActionEvent.ConversationOpened(conversationId),
        )
    }

    suspend fun provideAvatarBitmap(conversationId: ConversationId) =
        getAndPrepareAvatarUseCase.call(conversationId)

    fun onScreenResume() {
        notificationService.setVisibleConversation(currentConversationId)
    }

    fun onScreenPause() {
        notificationService.setVisibleConversation(null)
    }

    fun onAppLockUnlocked() = runAction {
        val currentSecureConversationState = currentViewState.secureConversationState
        if (currentSecureConversationState is SecureConversationState.Private) {
            updateViewState {
                copy(secureConversationState = SecureConversationState.Private.unlocked())
            }
            markConversationAsReadUseCase.call(currentConversationId)
            emitGlobalEventConversationOpened(currentConversationId)
        }
    }

    fun onTextChange(text: String) = runAction {
        updateViewState {
            copy(
                text = text,
            )
        }
    }
}
