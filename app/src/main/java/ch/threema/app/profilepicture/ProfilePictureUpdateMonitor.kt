package ch.threema.app.profilepicture

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ProfileEvent
import ch.threema.app.monitors.Monitor
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy
import ch.threema.app.tasks.TaskCreator
import ch.threema.domain.taskmanager.TriggerSource
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.core.component.KoinComponent

class ProfilePictureUpdateMonitor(
    private val globalEventFlows: GlobalEventFlows,
    private val appRestrictions: AppRestrictions,
    private val preferenceService: PreferenceService,
) : Monitor("ProfilePictureUpdateMonitor"), KoinComponent {
    private val multiDeviceManager: MultiDeviceManager? by injectNullableNonBinding()
    private val taskCreator: TaskCreator? by injectNullableNonBinding()

    override suspend fun run() {
        globalEventFlows.profiles
            .filterIsInstance<ProfileEvent.ProfilePictureUpdated>()
            .filter { it.triggerSource == TriggerSource.LOCAL }
            .collect {
                onProfilePictureChangedLocally()
            }
    }

    private fun onProfilePictureChangedLocally() {
        if (
            appRestrictions.isDisabledProfilePicReleaseSettings() ||
            preferenceService.getProfilePicRelease() != PreferenceService.PROFILEPIC_RELEASE_NOBODY
        ) {
            return
        }

        // a profile picture has been set so it's safe to assume the user wants others to see their pic
        preferenceService.setProfilePicRelease(PreferenceService.PROFILEPIC_RELEASE_EVERYONE)
        // Sync new policy setting to device group (if md is active)
        if (multiDeviceManager?.isMultiDeviceActive == true) {
            taskCreator?.scheduleReflectUserProfileShareWithPolicySyncTask(ProfilePictureSharePolicy.Policy.EVERYONE)
        }
    }
}
