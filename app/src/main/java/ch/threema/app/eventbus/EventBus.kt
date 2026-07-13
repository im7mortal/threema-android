package ch.threema.app.eventbus

import ch.threema.app.BuildConfig
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("EventBus")

class EventBus<T : Any>(
    private val coroutineScope: CoroutineScope,
) : Flow<T> {
    private val events = MutableSharedFlow<T>()

    fun emit(event: T) {
        coroutineScope.launch {
            if (BuildConfig.DEBUG) {
                logger.debug("Emitting event: {}", event)
            }
            events.emit(event)
        }
    }

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        events.collect(collector)
    }
}
