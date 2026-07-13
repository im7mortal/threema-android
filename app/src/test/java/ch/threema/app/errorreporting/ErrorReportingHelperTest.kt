package ch.threema.app.errorreporting

import ch.threema.app.preference.service.PreferenceService
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ErrorReportingHelperTest {

    @Test
    fun `no pending records`() = runTest {
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.NEVER_SEND
            },
            errorRecordStore = mockk {
                every { getErrorTypeIdsFromPendingRecords() } returns emptySet()
            },
            recentErrorTypeIdStore = mockk {
                every { getRecentErrorTypeIds() } returns emptySet()
            },
            sendErrorReportWorkerScheduler = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.DO_NOTHING, result)
    }

    @Test
    fun `always ask, with no recent error type ids`() = runTest {
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.ALWAYS_ASK
            },
            errorRecordStore = mockk {
                every { getErrorTypeIdsFromPendingRecords() } returns setOf(null)
            },
            recentErrorTypeIdStore = mockk {
                every { getRecentErrorTypeIds() } returns emptySet()
            },
            sendErrorReportWorkerScheduler = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.SHOW_DIALOG, result)
    }

    @Test
    fun `always ask, with some error type ids not recent`() = runTest {
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.ALWAYS_ASK
            },
            errorRecordStore = mockk {
                every { getErrorTypeIdsFromPendingRecords() } returns setOf(ErrorTypeId("A"), ErrorTypeId("B"))
            },
            recentErrorTypeIdStore = mockk {
                every { getRecentErrorTypeIds() } returns setOf(ErrorTypeId("B"), ErrorTypeId("C"))
            },
            sendErrorReportWorkerScheduler = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.SHOW_DIALOG, result)
    }

    @Test
    fun `always ask, with all error type ids recent`() = runTest {
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.ALWAYS_ASK
            },
            errorRecordStore = mockk {
                every { getErrorTypeIdsFromPendingRecords() } returns setOf(ErrorTypeId("A"), ErrorTypeId("B"))
            },
            recentErrorTypeIdStore = mockk {
                every { getRecentErrorTypeIds() } returns setOf(ErrorTypeId("A"), ErrorTypeId("B"))
            },
            sendErrorReportWorkerScheduler = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.DO_NOTHING, result)
    }

    @Test
    fun `always send`() = runTest {
        val recentErrorTypeIdStoreMock = mockk<RecentErrorTypeIdStore>(relaxed = true) {
            every { getRecentErrorTypeIds() } returns emptySet()
        }
        val errorRecordStoreMock = mockk<ErrorRecordStore> {
            every { getErrorTypeIdsFromPendingRecords() } returns setOf(null)
            every { confirmPendingRecords() } just runs
        }
        val sendErrorReportWorkerScheduler = mockk<SendErrorReportWorker.Scheduler> {
            every { schedule() } just runs
        }
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.ALWAYS_SEND
            },
            errorRecordStore = errorRecordStoreMock,
            recentErrorTypeIdStore = recentErrorTypeIdStoreMock,
            sendErrorReportWorkerScheduler = sendErrorReportWorkerScheduler,
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.DO_NOTHING, result)
        verify(exactly = 0) { recentErrorTypeIdStoreMock.recordAsRecent(any()) }
        verify(exactly = 1) { errorRecordStoreMock.confirmPendingRecords() }
        verify(exactly = 1) { sendErrorReportWorkerScheduler.schedule() }
    }

    @Test
    fun `never send`() = runTest {
        val recentErrorTypeIdStoreMock = mockk<RecentErrorTypeIdStore>(relaxed = true) {
            every { getRecentErrorTypeIds() } returns emptySet()
        }
        val errorRecordStoreMock = mockk<ErrorRecordStore> {
            every { getErrorTypeIdsFromPendingRecords() } returns setOf(null)
            every { deletePendingRecords() } just runs
        }
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk {
                every { getErrorReportingState() } returns PreferenceService.ErrorReportingState.NEVER_SEND
            },
            errorRecordStore = errorRecordStoreMock,
            recentErrorTypeIdStore = recentErrorTypeIdStoreMock,
            sendErrorReportWorkerScheduler = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val result = errorReportingHelper.processPendingErrorRecords()

        assertEquals(ErrorReportingHelper.CheckResult.DO_NOTHING, result)
        verify(exactly = 0) { recentErrorTypeIdStoreMock.recordAsRecent(any()) }
        verify(exactly = 1) { errorRecordStoreMock.deletePendingRecords() }
    }

    @Test
    fun `confirm records and schedule sending`() = runTest {
        val recentErrorTypeIdStoreMock = mockk<RecentErrorTypeIdStore>(relaxed = true) {
            every { getRecentErrorTypeIds() } returns setOf(ErrorTypeId("B"), ErrorTypeId("C"))
        }
        val errorRecordStoreMock = mockk<ErrorRecordStore> {
            every { getErrorTypeIdsFromPendingRecords() } returns setOf(null, ErrorTypeId("A"), ErrorTypeId("B"))
            every { confirmPendingRecords() } just runs
        }
        val sendErrorReportWorkerScheduler = mockk<SendErrorReportWorker.Scheduler> {
            every { schedule() } just runs
        }
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk(),
            errorRecordStore = errorRecordStoreMock,
            recentErrorTypeIdStore = recentErrorTypeIdStoreMock,
            sendErrorReportWorkerScheduler = sendErrorReportWorkerScheduler,
            dispatcherProvider = testDispatcherProvider(),
        )

        errorReportingHelper.confirmRecordsAndScheduleSending()

        verify(exactly = 1) { recentErrorTypeIdStoreMock.recordAsRecent(setOf(ErrorTypeId("A"), ErrorTypeId("B"))) }
        verify(exactly = 1) { errorRecordStoreMock.confirmPendingRecords() }
        verify(exactly = 1) { sendErrorReportWorkerScheduler.schedule() }
    }

    @Test
    fun `delete pending records`() = runTest {
        val recentErrorTypeIdStoreMock = mockk<RecentErrorTypeIdStore>(relaxed = true)
        val errorRecordStoreMock = mockk<ErrorRecordStore> {
            every { getErrorTypeIdsFromPendingRecords() } returns setOf(null, ErrorTypeId("A"), ErrorTypeId("B"))
            every { deletePendingRecords() } just runs
        }
        val errorReportingHelper = ErrorReportingHelper(
            preferenceService = mockk(),
            errorRecordStore = errorRecordStoreMock,
            recentErrorTypeIdStore = recentErrorTypeIdStoreMock,
            sendErrorReportWorkerScheduler = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        errorReportingHelper.deletePendingRecords()

        verify(exactly = 1) { recentErrorTypeIdStoreMock.recordAsRecent(setOf(ErrorTypeId("A"), ErrorTypeId("B"))) }
        verify(exactly = 1) { errorRecordStoreMock.deletePendingRecords() }
    }
}
