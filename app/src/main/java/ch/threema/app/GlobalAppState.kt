package ch.threema.app

import androidx.annotation.AnyThread
import ch.threema.base.utils.getThreemaLogger
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private val logger = getThreemaLogger("GlobalAppState")

class GlobalAppState {
    var lastLoggedIn: Instant? = null

    private val specialModeFlow = MutableStateFlow<SpecialMode?>(null)

    /**
     * Indicates whether the app is currently in a special mode, during which
     * normal operation is not possible. In particular, no connection to the chat server or mediator
     * is made and most screens are inaccessible while this returns a non-null value.
     */
    val specialMode: SpecialMode?
        get() = specialModeFlow.value

    /**
     * @see specialMode
     */
    fun watchSpecialMode(): StateFlow<SpecialMode?> =
        specialModeFlow

    /**
     * Puts the app into a specific [specialMode].
     * If the app is already in this mode, this request is ignored.
     * If the app is already in a different mode, an [IllegalStateException] is thrown.
     */
    @AnyThread
    fun startSpecialMode(specialMode: SpecialMode) {
        logger.info("Starting special mode: {}", specialMode)
        specialModeFlow.update { previousMode ->
            check(previousMode == null || previousMode == specialMode)
            specialMode
        }
    }

    /**
     * Ends a previously started special mode.
     * If the app is already no longer in a special mode, this request is ignored.
     * If the app is in a different mode than [specialMode], an [IllegalStateException] is thrown.
     */
    @AnyThread
    fun endSpecialMode(specialMode: SpecialMode) {
        logger.info("Ending special mode: {}", specialMode)
        specialModeFlow.update { previousMode ->
            check(previousMode == null || previousMode == specialMode)
            null
        }
    }

    enum class SpecialMode {
        BACKUP_CREATE,
        BACKUP_RESTORE,
        CROSS_PLATFORM_BACKUP_CREATE,
        CROSS_PLATFORM_BACKUP_RESTORE,
    }
}
