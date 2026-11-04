package ch.threema.app.libthreema.provider

import ch.threema.app.BuildConfig
import ch.threema.app.libthreema.conversion.toAvailabilityStatus
import ch.threema.app.libthreema.conversion.toLibthreemaWorkAvailabilityStatus
import ch.threema.app.libthreema.extensions.getLibthreemaUserProfilePicture
import ch.threema.app.libthreema.extensions.getLinkedEmailAddress
import ch.threema.app.libthreema.extensions.getLinkedPhoneNumber
import ch.threema.app.libthreema.extensions.getProfilePictureSharePolicy
import ch.threema.app.libthreema.extensions.setLibthreemaIdentityLinkEmailAddress
import ch.threema.app.libthreema.extensions.setLibthreemaIdentityLinkPhoneNumber
import ch.threema.app.libthreema.extensions.setLibthreemaUserProfilePicture
import ch.threema.app.libthreema.extensions.setProfilePictureSharePolicy
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.services.UserService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.common.takeUnlessEmpty
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.domain.models.Nickname
import ch.threema.libthreema.UserProfile as LibthreemaUserProfile
import ch.threema.libthreema.UserProfileProvider as LibthreemaUserProfileProvider
import ch.threema.libthreema.WorkAvailabilityStatus as LibthreemaWorkAvailabilityStatus

private val logger = getThreemaLogger("UserProfileProvider")

class UserProfileProvider(
    private val userService: UserService,
    private val preferenceService: PreferenceService,
    private val profilePictureRecipientsService: ProfilePictureRecipientsService,
    override val guaranteedIdentityProvider: GuaranteedIdentityProvider,
) : LibthreemaUserProfileProvider, IdentityAwareProvider() {
    override fun get(): LibthreemaUserProfile =
        withTimeMeasurement(logger, "get") {
            LibthreemaUserProfile(
                nickname = userService.publicNickname ?: "",
                profilePicture = userService.getLibthreemaUserProfilePicture(),
                profilePictureSharePolicy = preferenceService.getProfilePictureSharePolicy(
                    profilePictureRecipientsService = profilePictureRecipientsService,
                ),
                linkedPhoneNumber = userService.getLinkedPhoneNumber(),
                linkedEmailAddress = userService.getLinkedEmailAddress(),
                workAvailabilityStatus = if (BuildConfig.AVAILABILITY_STATUS_ENABLED) {
                    preferenceService.getAvailabilityStatus()?.toLibthreemaWorkAvailabilityStatus()
                        ?: LibthreemaWorkAvailabilityStatus.None
                } else {
                    null
                },
            )
        }

    override fun set(userProfile: LibthreemaUserProfile): Unit =
        withTimeMeasurement(logger, "set") {
            userService.persistPublicNickname(userProfile.nickname.takeUnlessEmpty()?.let(::Nickname))
            userService.setLibthreemaUserProfilePicture(userProfile.profilePicture)
            preferenceService.setProfilePictureSharePolicy(
                policy = userProfile.profilePictureSharePolicy,
                profilePictureRecipientsService = profilePictureRecipientsService,
            )
            userService.setLibthreemaIdentityLinkPhoneNumber(userProfile.linkedPhoneNumber)
            userService.setLibthreemaIdentityLinkEmailAddress(userProfile.linkedEmailAddress)
            if (BuildConfig.AVAILABILITY_STATUS_ENABLED) {
                preferenceService.setAvailabilityStatus(
                    userProfile.workAvailabilityStatus?.toAvailabilityStatus()
                        ?: AvailabilityStatus.None,
                )
            }
        }
}
