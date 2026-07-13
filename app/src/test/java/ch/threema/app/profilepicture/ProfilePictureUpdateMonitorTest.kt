package ch.threema.app.profilepicture

import ch.threema.app.eventbus.events.ProfileEvent
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.test.koinTestModuleRule
import ch.threema.domain.taskmanager.TriggerSource
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePictureUpdateMonitorTest {

    private lateinit var profileEvents: MutableSharedFlow<ProfileEvent>
    private lateinit var appRestrictionsMock: AppRestrictions
    private lateinit var preferenceServiceMock: PreferenceService
    private lateinit var multiDeviceManagerMock: MultiDeviceManager
    private lateinit var taskCreatorMock: TaskCreator
    private lateinit var profilePictureUpdateMonitor: ProfilePictureUpdateMonitor

    @get:Rule
    val koinTestRule = koinTestModuleRule {
        factory<MultiDeviceManager> { multiDeviceManagerMock }
        factory<TaskCreator> { taskCreatorMock }
    }

    @BeforeTest
    fun setUp() {
        profileEvents = MutableSharedFlow()
        appRestrictionsMock = mockk {
            every { isDisabledProfilePicReleaseSettings() } returns false
        }
        preferenceServiceMock = mockk {
            every { getProfilePicRelease() } returns PreferenceService.PROFILEPIC_RELEASE_NOBODY
            every { setProfilePicRelease(any()) } just runs
        }
        multiDeviceManagerMock = mockk()
        taskCreatorMock = mockk(relaxed = true)
        profilePictureUpdateMonitor = ProfilePictureUpdateMonitor(
            globalEventFlows = mockk {
                every { profiles } returns profileEvents
            },
            appRestrictions = appRestrictionsMock,
            preferenceService = preferenceServiceMock,
        )
    }

    @Test
    fun `profile picture visibility is updated when profile picture is set locally`() = runTest {
        every { multiDeviceManagerMock.isMultiDeviceActive } returns false
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            profilePictureUpdateMonitor.run()
        }

        profileEvents.emit(ProfileEvent.ProfilePictureUpdated(triggerSource = TriggerSource.LOCAL))

        verify(exactly = 1) { preferenceServiceMock.setProfilePicRelease(PreferenceService.PROFILEPIC_RELEASE_EVERYONE) }
        verify(exactly = 0) { taskCreatorMock.scheduleReflectUserProfileShareWithPolicySyncTask(any()) }

        monitorJob.cancel()
    }

    @Test
    fun `profile picture visibility is updated when profile picture is set locally, with multi-device`() = runTest {
        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            profilePictureUpdateMonitor.run()
        }

        profileEvents.emit(ProfileEvent.ProfilePictureUpdated(triggerSource = TriggerSource.LOCAL))

        verify(exactly = 1) { preferenceServiceMock.setProfilePicRelease(PreferenceService.PROFILEPIC_RELEASE_EVERYONE) }
        verify(exactly = 1) { taskCreatorMock.scheduleReflectUserProfileShareWithPolicySyncTask(ProfilePictureSharePolicy.Policy.EVERYONE) }

        monitorJob.cancel()
    }

    @Test
    fun `profile picture visibility is not updated when profile picture is set remotely`() = runTest {
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            profilePictureUpdateMonitor.run()
        }

        profileEvents.emit(ProfileEvent.ProfilePictureUpdated(triggerSource = TriggerSource.REMOTE))

        verify(exactly = 0) { preferenceServiceMock.setProfilePicRelease(any()) }

        monitorJob.cancel()
    }

    @Test
    fun `profile picture visibility is not updated when profile picture is set by sync`() = runTest {
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            profilePictureUpdateMonitor.run()
        }

        profileEvents.emit(ProfileEvent.ProfilePictureUpdated(triggerSource = TriggerSource.SYNC))

        verify(exactly = 0) { preferenceServiceMock.setProfilePicRelease(any()) }

        monitorJob.cancel()
    }

    @Test
    fun `profile picture visibility is not updated when profile pic release settings are disabled`() = runTest {
        every { appRestrictionsMock.isDisabledProfilePicReleaseSettings() } returns true
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            profilePictureUpdateMonitor.run()
        }

        profileEvents.emit(ProfileEvent.ProfilePictureUpdated(triggerSource = TriggerSource.LOCAL))

        verify(exactly = 0) { preferenceServiceMock.setProfilePicRelease(any()) }

        monitorJob.cancel()
    }

    @Test
    fun `profile picture visibility is not updated when already set to value other than nobody`() = runTest {
        every { preferenceServiceMock.getProfilePicRelease() } returns PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST
        val monitorJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            profilePictureUpdateMonitor.run()
        }

        profileEvents.emit(ProfileEvent.ProfilePictureUpdated(triggerSource = TriggerSource.LOCAL))

        verify(exactly = 0) { preferenceServiceMock.setProfilePicRelease(any()) }

        monitorJob.cancel()
    }
}
