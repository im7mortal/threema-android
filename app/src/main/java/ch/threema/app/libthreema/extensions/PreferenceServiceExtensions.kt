package ch.threema.app.libthreema.extensions

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.libthreema.ProfilePictureSharePolicy as LibthreemaProfilePictureSharePolicy

fun PreferenceService.getProfilePictureSharePolicy(
    profilePictureRecipientsService: ProfilePictureRecipientsService,
): LibthreemaProfilePictureSharePolicy =
    when (val profilePicReleaseInt = getProfilePicRelease()) {
        PreferenceService.PROFILEPIC_RELEASE_NOBODY -> LibthreemaProfilePictureSharePolicy.Nobody
        PreferenceService.PROFILEPIC_RELEASE_EVERYONE -> LibthreemaProfilePictureSharePolicy.Everyone
        PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST -> LibthreemaProfilePictureSharePolicy.AllowList(
            profilePictureRecipientsService.all.toList(),
        )

        else -> error("Invalid profile picture release value: $profilePicReleaseInt")
    }

fun PreferenceService.setProfilePictureSharePolicy(
    policy: LibthreemaProfilePictureSharePolicy,
    profilePictureRecipientsService: ProfilePictureRecipientsService,
) = when (policy) {
    is LibthreemaProfilePictureSharePolicy.AllowList -> {
        profilePictureRecipientsService.replaceAll(
            policy.v1
                .toTypedArray(),
        )
        setProfilePicRelease(PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST)
    }

    is LibthreemaProfilePictureSharePolicy.Everyone -> setProfilePicRelease(PreferenceService.PROFILEPIC_RELEASE_EVERYONE)
    is LibthreemaProfilePictureSharePolicy.Nobody -> setProfilePicRelease(PreferenceService.PROFILEPIC_RELEASE_NOBODY)
}
