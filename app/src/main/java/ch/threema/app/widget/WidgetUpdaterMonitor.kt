package ch.threema.app.widget

import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.monitors.Monitor
import ch.threema.app.services.LockAppService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.runWithRetry
import ch.threema.common.waitAtMost
import ch.threema.localcrypto.MasterKeyProvider
import ch.threema.storage.models.MessageModel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest

private val logger = getThreemaLogger("WidgetUpdaterMonitor")

// TODO(ANDR-4706): This monitor should also take into account:
//  - whether Remote Secrets is in use (as the widget is disabled if it is)
//  - updates to groups (e.g. renames)
//  - updates to contacts (e.g. profile picture changes)
//  - updates to preferences__notification_preview and preferences__contact_format
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WidgetUpdaterMonitor(
    private val masterKeyProvider: MasterKeyProvider,
    private val globalEventFlows: GlobalEventFlows,
    private val lockAppService: LockAppService,
    private val widgetUpdater: WidgetUpdater,
) : Monitor("WidgetUpdaterMonitor") {
    override suspend fun run() {
        masterKeyProvider.watchMasterKey()
            .map { it != null }
            .transformLatest { isUnlocked ->
                if (isUnlocked) {
                    merge(
                        watchAppLock(),
                        watchConversationEvents(),
                        watchRelevantMessageEvents(),
                    )
                        .collect(this)
                } else {
                    // The widgets should also be updated when the master key is/gets locked
                    emit(Unit)
                }
            }
            .debounce(1.seconds)
            .collectLatest {
                updateWidgets()
            }
    }

    private fun watchConversationEvents(): Flow<*> =
        globalEventFlows.conversations

    private fun watchAppLock(): Flow<*> =
        lockAppService.watchLockState()

    private fun watchRelevantMessageEvents(): Flow<*> =
        globalEventFlows.messages
            .map { event ->
                when (event) {
                    is MessageEvent.MessageDeletedForAll -> listOf(event.message)
                    is MessageEvent.MessageEdited -> listOf(event.message)
                    is MessageEvent.NewMessage -> listOf(event.message)
                    is MessageEvent.MessageRemovedLocally -> listOf(event.message)
                    is MessageEvent.MessagesUpdated -> event.messages
                }
            }
            .filter { messages ->
                messages.any(::isMessageRelevantForWidget)
            }

    private fun isMessageRelevantForWidget(message: MessageModel) =
        !message.isOutbox && !message.isStatusMessage && !message.isRead

    private suspend fun updateWidgets() {
        waitAtMost(5.minutes) {
            runWithRetry(
                action = {
                    widgetUpdater.updateWidgets()
                },
                onException = { e, attempt ->
                    logger.error("Failed to update widgets (attempt {})", attempt, e)
                },
                getRetryDelay = { attempt ->
                    (4L shl attempt).seconds
                },
            )
        }
    }
}
