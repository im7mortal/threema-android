package ch.threema.app.applock

import ch.threema.app.services.LockAppService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.test.koinTestModuleRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockNotificationUpdaterMonitorTest {

    private lateinit var lockStateFlow: MutableStateFlow<LockAppService.LockState>
    private lateinit var notificationServiceMock: NotificationService
    private lateinit var appLockNotificationUpdaterMonitor: AppLockNotificationUpdaterMonitor

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory<NotificationService> { notificationServiceMock }
    }

    @BeforeTest
    fun setUp() {
        lockStateFlow = MutableStateFlow(LockAppService.LockState.UNLOCKED)
        notificationServiceMock = mockk(relaxed = true)
        appLockNotificationUpdaterMonitor = AppLockNotificationUpdaterMonitor(
            lockAppService = mockk {
                every { watchLockState() } returns lockStateFlow
            },
        )
    }

    @Test
    fun `notification is not cancelled when initially unlocked`() = runTest {
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            appLockNotificationUpdaterMonitor.run()
        }

        verify(exactly = 0) {
            notificationServiceMock.cancelAppLockedNewMessagesNotification()
        }

        monitorJob.cancel()
    }

    @Test
    fun `notification is not cancelled when initially locked`() = runTest {
        lockStateFlow.value = LockAppService.LockState.LOCKED
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            appLockNotificationUpdaterMonitor.run()
        }

        verify(exactly = 0) {
            notificationServiceMock.cancelAppLockedNewMessagesNotification()
        }

        monitorJob.cancel()
    }

    @Test
    fun `notification is not cancelled when app gets locked`() = runTest {
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            appLockNotificationUpdaterMonitor.run()
        }

        lockStateFlow.value = LockAppService.LockState.LOCKED

        verify(exactly = 0) {
            notificationServiceMock.cancelAppLockedNewMessagesNotification()
        }

        monitorJob.cancel()
    }

    @Test
    fun `notification is cancelled when app gets unlocked`() = runTest {
        lockStateFlow.value = LockAppService.LockState.LOCKED
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            appLockNotificationUpdaterMonitor.run()
        }

        lockStateFlow.value = LockAppService.LockState.UNLOCKED

        verify(exactly = 1) {
            notificationServiceMock.cancelAppLockedNewMessagesNotification()
        }

        monitorJob.cancel()
    }

    @Test
    fun `notification is cancelled every time the app gets unlocked`() = runTest {
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            appLockNotificationUpdaterMonitor.run()
        }

        repeat(3) {
            lockStateFlow.value = LockAppService.LockState.LOCKED
            delay(1.seconds)
            lockStateFlow.value = LockAppService.LockState.UNLOCKED
            delay(1.seconds)
        }

        verify(exactly = 3) {
            notificationServiceMock.cancelAppLockedNewMessagesNotification()
        }

        monitorJob.cancel()
    }
}
