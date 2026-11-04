package ch.threema.app.eventbus

import androidx.annotation.VisibleForTesting
import ch.threema.app.BuildConfig
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("EventBus")

/**
 * ### Ordering guarantee
 *
 * Events are delivered to every collector in the order they were passed to [emit]. Specifically, if a single thread calls
 * `emit(a)` and then `emit(b)`, every collector observes `a` before `b`.
 *
 * Note that **no** total order exists across threads that emit concurrently.
 *
 * ### Delivery
 *
 * An [emit] never suspends or blocks the calling thread. Back-pressure is absorbed by the queue instead.
 */
class EventBus<T : Any>(
    coroutineScope: CoroutineScope,
) : Flow<T> {
    private val events = MutableSharedFlow<T>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)

    /**
     * Serializes emissions. Unbounded so that [emit] can stay non-blocking.
     */
    private val queue = Channel<T>(Channel.UNLIMITED)

    init {
        coroutineScope.launch {
            for (event in queue) {
                if (BuildConfig.DEBUG) {
                    logger.debug("Emitting event: {}", event)
                }
                events.emit(event)
            }
        }
    }

    /**
     * Publishes an event. Returns immediately without suspending or blocking.
     */
    fun emit(event: T) {
        if (queue.trySend(event).isFailure) {
            // Only reachable if the bus has been shut down.
            logger.error("Failed to enqueue event, it will not be delivered")
        }
    }

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        events.collect(collector)
    }

    /**
     * Suspends until at least [count] collectors are subscribed.
     */
    @VisibleForTesting
    suspend fun awaitSubscribers(count: Int = 1) {
        events.subscriptionCount.first { subscribers -> subscribers >= count }
    }

    private companion object {
        /**
         * How far a collector may fall behind before the bus throttles. Purely a decoupling buffer: nothing is dropped when it is
         * exceeded, the forwarder simply suspends.
         */
        const val EVENT_BUFFER_CAPACITY = 64
    }
}
