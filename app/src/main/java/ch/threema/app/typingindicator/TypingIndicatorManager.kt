package ch.threema.app.typingindicator

import ch.threema.common.DispatcherProvider
import ch.threema.common.mapState
import ch.threema.domain.types.Identity
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

class TypingIndicatorManager(
    dispatcherProvider: DispatcherProvider,
    private val timeSource: TimeSource.WithComparableMarks,
) : TypingIndicatorProvider {
    private val coroutineScope = CoroutineScope(dispatcherProvider.worker)

    /**
     * A map of identities to the time at which their typing indicator expires
     */
    private val typingIdentities = MutableStateFlow<Map<Identity, ComparableTimeMark>>(emptyMap())

    init {
        coroutineScope.launch {
            typingIdentities.collectLatest {
                var nextExpiration = removeExpiredTypingIndicators()
                while (nextExpiration != null) {
                    delay(nextExpiration)
                    nextExpiration = removeExpiredTypingIndicators()
                }
            }
        }
    }

    private fun removeExpiredTypingIndicators(): Duration? {
        val now = timeSource.markNow()
        val updatedTypingIdentities = typingIdentities.updateAndGet {
            it.filterValues { expiration -> expiration > now }
        }
        val nextExpiring = updatedTypingIdentities.values.minOrNull()
            ?: return null
        return nextExpiring - now
    }

    fun setIsTyping(identity: Identity, isTyping: Boolean) {
        typingIdentities.update { previousTypingIdentities ->
            if (isTyping) {
                previousTypingIdentities + Pair(identity, timeSource.markNow() + RECEIVE_TIMEOUT)
            } else {
                previousTypingIdentities - identity
            }
        }
    }

    override fun isTyping(identity: Identity): Boolean =
        typingIdentities.value.containsKey(identity)

    override fun watchTypingIdentities(): StateFlow<Set<Identity>> =
        typingIdentities.mapState { it.keys }

    companion object {
        private val RECEIVE_TIMEOUT = 15.seconds
    }
}
