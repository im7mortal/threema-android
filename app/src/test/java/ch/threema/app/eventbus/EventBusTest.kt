package ch.threema.app.eventbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * These tests deliberately back the bus with a **real multithreaded dispatcher** rather than a `TestDispatcher`. The race only exists on a real pool,
 * so that is what has to be exercised here.
 */
class EventBusTest {

    private data class TaggedEvent(
        val emitterId: Int,
        val sequence: Int,
    )

    @Test
    fun `events are delivered in emission order`() = runTest(timeout = TEST_TIMEOUT) {
        val busScope = realBusScope()
        try {
            val eventBus = EventBus<Int>(busScope)
            val received = Channel<Int>(Channel.UNLIMITED)
            busScope.launch { eventBus.collect { received.send(it) } }
            eventBus.awaitSubscribers()

            repeat(EVENT_COUNT) { eventBus.emit(it) }

            val actual = List(EVENT_COUNT) { received.receive() }
            assertEquals(
                expected = List(EVENT_COUNT) { it },
                actual = actual,
                message = "events were delivered out of order",
            )
        } finally {
            busScope.cancel()
        }
    }

    @Test
    fun `each emitter's own sequence is preserved when emitters run concurrently`() = runTest(timeout = TEST_TIMEOUT) {
        val busScope = realBusScope()
        try {
            val eventBus = EventBus<TaggedEvent>(busScope)
            val received = Channel<TaggedEvent>(Channel.UNLIMITED)
            busScope.launch { eventBus.collect { received.send(it) } }
            eventBus.awaitSubscribers()

            withContext(Dispatchers.Default) {
                (0 until EMITTER_COUNT)
                    .map { emitterId ->
                        async {
                            repeat(EVENTS_PER_EMITTER) { sequence ->
                                eventBus.emit(TaggedEvent(emitterId = emitterId, sequence = sequence))
                            }
                        }
                    }
                    .awaitAll()
            }

            val all = List(EMITTER_COUNT * EVENTS_PER_EMITTER) { received.receive() }

            // No total order is promised across concurrent emitters, only that each emitter's own subsequence is intact.
            for (emitterId in 0 until EMITTER_COUNT) {
                assertEquals(
                    expected = List(EVENTS_PER_EMITTER) { it },
                    actual = all.filter { it.emitterId == emitterId }.map(TaggedEvent::sequence),
                    message = "events from emitter $emitterId were reordered or lost",
                )
            }
        } finally {
            busScope.cancel()
        }
    }

    @Test
    fun `every collector receives all events in the same order`() = runTest(timeout = TEST_TIMEOUT) {
        val busScope = realBusScope()
        try {
            val eventBus = EventBus<Int>(busScope)
            val receivedByFirst = Channel<Int>(Channel.UNLIMITED)
            val receivedBySecond = Channel<Int>(Channel.UNLIMITED)
            busScope.launch { eventBus.collect { receivedByFirst.send(it) } }
            busScope.launch { eventBus.collect { receivedBySecond.send(it) } }
            eventBus.awaitSubscribers(count = 2)

            repeat(EVENT_COUNT) { eventBus.emit(it) }

            val expected = List(EVENT_COUNT) { it }
            assertEquals(expected, List(EVENT_COUNT) { receivedByFirst.receive() }, "first collector")
            assertEquals(expected, List(EVENT_COUNT) { receivedBySecond.receive() }, "second collector")
        } finally {
            busScope.cancel()
        }
    }

    /**
     * Covers the two properties that the unbounded queue plus a suspending (rather than dropping) forwarder are there to provide:
     * emitting never blocks the caller, and back-pressure costs latency rather than events.
     *
     * If [EventBus.emit] ever became a suspending or blocking call, this test dead-locks instead of failing, which the timeout
     * turns into a failure.
     */
    @Test
    fun `emitting never blocks the caller and a slow collector loses no events`() = runTest(timeout = TEST_TIMEOUT) {
        val busScope = realBusScope()
        try {
            val eventBus = EventBus<Int>(busScope)
            val release = CompletableDeferred<Unit>()
            val received = Channel<Int>(Channel.UNLIMITED)
            busScope.launch {
                eventBus.collect { value ->
                    // Hold the only collector so the bus is fully back-pressured while we emit.
                    release.await()
                    received.send(value)
                }
            }
            eventBus.awaitSubscribers()

            // Emits far more than EVENT_BUFFER_CAPACITY, so the forwarder is guaranteed to be suspended part way through.
            withContext(Dispatchers.Default) {
                repeat(EVENT_COUNT) { eventBus.emit(it) }
            }

            release.complete(Unit)

            assertEquals(
                expected = List(EVENT_COUNT) { it },
                actual = List(EVENT_COUNT) { received.receive() },
                message = "a back-pressured bus dropped or reordered events",
            )
        } finally {
            busScope.cancel()
        }
    }

    private fun realBusScope() = CoroutineScope(Dispatchers.Default)

    private companion object {
        const val EVENT_COUNT = 1_000
        const val EMITTER_COUNT = 4
        const val EVENTS_PER_EMITTER = 250
        val TEST_TIMEOUT = 3.seconds
    }
}
