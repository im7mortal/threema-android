package ch.threema.app.framework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.common.awaitAtLeastOneSubscriber
import ch.threema.common.awaitNonNull
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The base implementation for view models, very loosely following an MVI-like approach.
 *
 * The view model holds a singular view state, which is created and transformed by the view model and may be consumed by the view/UI layer.
 *
 * The view model may also emit events, e.g. to indicate to the view/UI-layer that specific one-off operations need to be performed.
 *
 * The view/UI layer may interact with the view model by calling methods on it. The naming scheme for these methods is that they use the "on" prefix
 * and describe the event that triggered them, NOT what the resulting action does. So e.g. if a "Submit" button is clicked in the UI,
 * the corresponding method might be called "onClickedSubmitButton", not "submitForm" or anything else that indicates the details of the action.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseViewModel<ViewState : Any, ViewEvent : Any> : ViewModel() {

    private val _viewState = MutableStateFlow<ViewState?>(null)

    /**
     * The view model's view state. Will be `null` until the view model has completed its initialization.
     * Once initialized, it will never be `null` again.
     */
    val viewState = _viewState.asStateFlow()

    private val _events = MutableSharedFlow<ViewEvent>()

    @JvmField
    val events = _events.asSharedFlow()

    private val isActive = _viewState.subscriptionCount
        .mapLatest { count ->
            if (count > 0) {
                true
            } else {
                // When the last subscriber disappears, we wait a few seconds in case they come back, to avoid unnecessarily deactivating
                // and reactivating the view model. This would primarily happen when an activity is recreated due to a configuration change.
                delay(5.seconds)
                false
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    init {
        viewModelScope.launch {
            isActive.first { it }
            _viewState.value = initialize()
        }
    }

    /**
     * Subclasses need to implement this method to initialize the view model and its view state.
     *
     * Within the implementation, one-time operations may be launched, and it is the recommended place to call [runWhenActive] from.
     *
     * If a view model wishes to halt itself, e.g. due to invalid parameters or encountering an exception, it should emit an appropriate event
     * to indicate this to the hosting screen such that the screen can display an error or close itself. The [initialize] method should then
     * suspend forever.
     */
    protected abstract suspend fun initialize(): ViewState

    private suspend fun awaitInitialized() {
        _viewState.awaitNonNull()
    }

    /**
     * This helper method can be used to wrap public methods on a view model to receive events from outside
     * and run appropriate actions, without leaking the [Job] instance returned by [launchAction].
     * @see [launchAction]
     */
    protected fun runAction(action: suspend CoroutineScope.() -> Unit) {
        launchAction(action = action)
    }

    /**
     * Launches an action, typically initiated by an event outside the view model such as a user interaction,
     * in the scope of the view model.
     * The action may alter the view state or emit view events.
     * The action will suspend if the view model is not yet initialized.
     * By default, the action runs on the main thread.
     * If multiple actions are run, there is no guarantee about their execution order.
     *
     * If you don't need the returned [Job], use [runAction] instead.
     */
    protected fun launchAction(
        context: CoroutineContext = EmptyCoroutineContext,
        action: suspend CoroutineScope.() -> Unit,
    ): Job =
        viewModelScope.launch(context) {
            awaitInitialized()
            action()
        }

    /**
     * Runs [action] whenever the view model becomes active, i.e., when at least 1 subscriber is collecting its view state.
     * Guaranteed to run only after the view model is initialized.
     *
     * This is typically called from inside [initialize], but may also be used differently.
     *
     * This can be used for operations that need to run every time the view model becomes active, or for long-running operations that should
     * remain active while the view model is active, such as collecting from other data sources or subscribing to listeners.
     *
     * Will be canceled when the view model becomes inactive, i.e., when all subscribers have stopped collecting the view state, with a small
     * delay to avoid unnecessarily stopping and restarting.
     */
    protected fun runWhenActive(
        context: CoroutineContext = EmptyCoroutineContext,
        action: suspend CoroutineScope.() -> Unit,
    ): Job =
        viewModelScope.launch(context) {
            awaitInitialized()
            isActive.collectLatest { isActive ->
                if (isActive) {
                    action()
                }
            }
        }

    protected suspend fun updateViewState(update: ViewState.() -> ViewState) {
        awaitInitialized()
        _viewState.update { it!!.update() }
    }

    /**
     * Emits an event.
     * Will suspend if no subscribers are present to avoid losing events.
     * It is possible to emit events before the view model is fully initialized.
     */
    protected suspend fun emitEvent(event: ViewEvent) {
        _events.awaitAtLeastOneSubscriber()
        _events.emit(event)
    }

    /**
     * The current view state. Should only be accessed in places where it is guaranteed that the view model has been initialized,
     * such as within the lambda passed to [runAction], [launchAction], or [runWhenActive].
     *
     * @throws IllegalStateException if the view model has not been initialized yet
     */
    protected val currentViewState: ViewState
        get() = _viewState.value ?: error("View model not yet initialized")
}
