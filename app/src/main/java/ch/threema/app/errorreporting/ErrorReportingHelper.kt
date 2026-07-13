package ch.threema.app.errorreporting

import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.withContext

class ErrorReportingHelper(
    private val preferenceService: PreferenceService,
    private val errorRecordStore: ErrorRecordStore,
    private val recentErrorTypeIdStore: RecentErrorTypeIdStore,
    private val sendErrorReportWorkerScheduler: SendErrorReportWorker.Scheduler,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun processPendingErrorRecords(): CheckResult = withContext(dispatcherProvider.io) {
        if (!hasPendingRecords()) {
            return@withContext CheckResult.DO_NOTHING
        }
        return@withContext when (preferenceService.getErrorReportingState()) {
            PreferenceService.ErrorReportingState.ALWAYS_ASK -> CheckResult.SHOW_DIALOG
            PreferenceService.ErrorReportingState.ALWAYS_SEND -> {
                errorRecordStore.confirmPendingRecords()
                sendErrorReportWorkerScheduler.schedule()
                CheckResult.DO_NOTHING
            }
            PreferenceService.ErrorReportingState.NEVER_SEND -> {
                errorRecordStore.deletePendingRecords()
                CheckResult.DO_NOTHING
            }
        }
    }

    private suspend fun hasPendingRecords() = withContext(dispatcherProvider.io) {
        (errorRecordStore.getErrorTypeIdsFromPendingRecords() - recentErrorTypeIdStore.getRecentErrorTypeIds()).isNotEmpty()
    }

    suspend fun deletePendingRecords() = withContext(dispatcherProvider.io) {
        recordErrorTypeIdsFromPendingRecordsAsRecent()
        errorRecordStore.deletePendingRecords()
    }

    suspend fun confirmRecordsAndScheduleSending() = withContext(dispatcherProvider.io) {
        recordErrorTypeIdsFromPendingRecordsAsRecent()
        errorRecordStore.confirmPendingRecords()
        sendErrorReportWorkerScheduler.schedule()
    }

    private fun recordErrorTypeIdsFromPendingRecordsAsRecent() {
        recentErrorTypeIdStore.recordAsRecent(errorRecordStore.getErrorTypeIdsFromPendingRecords().filterNotNull().toSet())
    }

    enum class CheckResult {
        DO_NOTHING,
        SHOW_DIALOG,
    }
}
