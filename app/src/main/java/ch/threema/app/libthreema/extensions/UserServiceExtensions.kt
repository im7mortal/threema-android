package ch.threema.app.libthreema.extensions

import ch.threema.app.AppConstants.EMAIL_LINKED_PLACEHOLDER
import ch.threema.app.AppConstants.PHONE_LINKED_PLACEHOLDER
import ch.threema.app.services.UserService
import ch.threema.libthreema.IdentityLinkEmailAddress as LibthreemaIdentityLinkEmailAddress
import ch.threema.libthreema.IdentityLinkPhoneNumber as LibthreemaIdentityLinkPhoneNumber
import ch.threema.libthreema.ProfilePicture as LibthreemaProfilePicture
import ch.threema.libthreema.ProfilePictureMediaType as LibthreemaProfilePictureMediaType

fun UserService.getLinkedPhoneNumber(): LibthreemaIdentityLinkPhoneNumber? = linkedMobileE164?.let { linkedMobileE164 ->
    return if (linkedMobileE164 == PHONE_LINKED_PLACEHOLDER) {
        LibthreemaIdentityLinkPhoneNumber.Unknown
    } else {
        LibthreemaIdentityLinkPhoneNumber.PhoneNumber(
            linkedMobileE164,
        )
    }
}

fun UserService.getLinkedEmailAddress(): LibthreemaIdentityLinkEmailAddress? = linkedEmail?.let { linkedEmail ->
    return if (linkedEmail == EMAIL_LINKED_PLACEHOLDER) {
        LibthreemaIdentityLinkEmailAddress.Unknown
    } else {
        LibthreemaIdentityLinkEmailAddress.EmailAddress(
            linkedEmail,
        )
    }
}

fun UserService.setLibthreemaIdentityLinkPhoneNumber(
    identityLinkPhoneNumber: LibthreemaIdentityLinkPhoneNumber?,
) {
    val phoneNumber = when (identityLinkPhoneNumber) {
        is LibthreemaIdentityLinkPhoneNumber.PhoneNumber -> identityLinkPhoneNumber.v1
        is LibthreemaIdentityLinkPhoneNumber.Unknown -> PHONE_LINKED_PLACEHOLDER
        null -> null
    }

    persistPhoneIdentityLink(phoneNumber)
}

fun UserService.setLibthreemaIdentityLinkEmailAddress(
    identityLinkEmailAddress: LibthreemaIdentityLinkEmailAddress?,
) {
    val emailAddress = when (identityLinkEmailAddress) {
        is LibthreemaIdentityLinkEmailAddress.EmailAddress -> identityLinkEmailAddress.v1
        is LibthreemaIdentityLinkEmailAddress.Unknown -> EMAIL_LINKED_PLACEHOLDER
        null -> null
    }

    persistEmailIdentityLink(emailAddress)
}

fun UserService.getLibthreemaUserProfilePicture(): LibthreemaProfilePicture? =
    userProfilePicture?.bytes?.let { profilePictureBytes ->
        LibthreemaProfilePicture(
            mediaType = LibthreemaProfilePictureMediaType.JPEG,
            data = profilePictureBytes,
        )
    }

fun UserService.setLibthreemaUserProfilePicture(profilePicture: LibthreemaProfilePicture?) =
    persistUserProfilePicture(profilePicture?.data)
