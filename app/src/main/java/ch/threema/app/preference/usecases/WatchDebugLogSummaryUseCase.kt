package ch.threema.app.preference.usecases

import ch.threema.app.logging.DebugLogHelper
import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.ByteSize
import ch.threema.common.DispatcherProvider
import ch.threema.logging.backend.DebugLogFileManager
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest

@OptIn(ExperimentalCoroutinesApi::class)
class WatchDebugLogSummaryUseCase(
    private val preferenceService: PreferenceService,
    private val debugLogFileManager: DebugLogFileManager,
    private val debugLogHelper: DebugLogHelper,
    private val dispatcherProvider: DispatcherProvider,
) {
    fun call(): Flow<DebugLogMetaData?> =
        preferenceService.watchDebugLogEnabledTimestamp()
            .transformLatest { enabledSince ->
                if (enabledSince != null || debugLogHelper.isDebugLogFileLoggingForceEnabled()) {
                    while (true) {
                        emit(
                            DebugLogMetaData(
                                enabledSince = enabledSince,
                                size = debugLogFileManager.getTotalLogFileSize(),
                            ),
                        )

                        // Periodically re-calculate the total file size of the log
                        delay(10.seconds)
                    }
                } else {
                    emit(null)
                }
            }
            .flowOn(dispatcherProvider.io)

    data class DebugLogMetaData(
        val enabledSince: Instant?,
        val size: ByteSize,
    )
}
