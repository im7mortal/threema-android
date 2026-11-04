package ch.threema.app.threemasafe

import android.graphics.Bitmap
import ch.threema.android.toJpegByteArray
import ch.threema.android.transform
import ch.threema.android.use
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.Companion.PROFILEPIC_RELEASE_ALLOW_LIST
import ch.threema.app.preference.service.PreferenceService.Companion.PROFILEPIC_RELEASE_EVERYONE
import ch.threema.app.services.FileService
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.services.UserService
import ch.threema.app.threemasafe.ThreemaSafeUserSection.LinkItem
import ch.threema.app.threemasafe.ThreemaSafeUserSection.LinkItemType
import ch.threema.app.threemasafe.ThreemaSafeUserSection.ProfilePicRelease
import ch.threema.app.threemasafe.ThreemaSafeUserSection.ThreemaSafeUserInfo
import ch.threema.app.utils.BitmapUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.takeUnlessEmpty
import ch.threema.domain.stores.IdentityStore
import kotlin.io.encoding.Base64
import kotlin.math.max

private val logger = getThreemaLogger("ThreemaSafeUserSectionCreator")

class ThreemaSafeUserSectionCreator(
    private val identityStore: IdentityStore,
    private val userService: UserService,
    private val fileService: FileService,
    private val preferenceService: PreferenceService,
    private val profilePictureRecipientsService: ProfilePictureRecipientsService,
) {
    fun create(): ThreemaSafeUserSection {
        val threemaSafeUserInfo = ThreemaSafeUserInfo(
            privateKey = getPrivateKey(),
            nickname = userService.publicNickname,
            profilePic = getProfilePicture(),
            profilePicRelease = getProfilePictureRelease(),
            links = getLinks(),
        )

        return ThreemaSafeUserSection.create(threemaSafeUserInfo)
    }

    private fun getPrivateKey(): String {
        val privateKey = identityStore.getPrivateKey()
            ?: error("Cannot create safe backup if no private key exists")
        return Base64.encode(privateKey)
    }

    private fun getProfilePicture(): String? = try {
        getResizedProfilePicture()?.use { image ->
            image.toJpegByteArray(quality = PROFILE_PICTURE_QUALITY)?.let(Base64::encode)
        }
    } catch (e: Exception) {
        logger.error("Could not add profile picture to safe backup; continuing without setting profile picture", e)
        null
    }

    private fun getProfilePictureRelease(): ProfilePicRelease = when (preferenceService.getProfilePicRelease()) {
        PROFILEPIC_RELEASE_EVERYONE -> ProfilePicRelease.Everyone
        PROFILEPIC_RELEASE_ALLOW_LIST ->
            profilePictureRecipientsService.all
                .toList()
                .takeUnlessEmpty()
                ?.let { identities -> ProfilePicRelease.AllowList(identities) }
                ?: ProfilePicRelease.None
        else -> ProfilePicRelease.None
    }

    private fun getResizedProfilePicture(): Bitmap? {
        val myIdentity = identityStore.requireIdentityString()
        return fileService.getUserDefinedProfilePicture(myIdentity)?.let { image ->
            // Scale image - assume profile pics are always square
            if (max(image.width, image.height) > PROFILE_PICTURE_MAX_WIDTH) {
                image.transform { image ->
                    BitmapUtil.resizeBitmap(image, PROFILE_PICTURE_MAX_WIDTH, PROFILE_PICTURE_MAX_WIDTH)
                }
            } else {
                image
            }
        }
    }

    private fun getLinks(): List<LinkItem> = buildList {
        if (userService.mobileLinkingState == UserService.LinkingState_LINKED) {
            val phoneNumber = userService.linkedMobileE164?.removePrefix("+")
            if (phoneNumber != null) {
                add(
                    LinkItem(
                        type = LinkItemType.PHONE,
                        name = null,
                        value = phoneNumber,
                    ),
                )
            }
        }

        if (userService.emailLinkingState == UserService.LinkingState_LINKED) {
            val emailAddress = userService.linkedEmail
            if (emailAddress.isNotBlank()) {
                add(
                    LinkItem(
                        type = LinkItemType.EMAIL,
                        name = null,
                        value = emailAddress,
                    ),
                )
            }
        }
    }

    companion object {
        private const val PROFILE_PICTURE_QUALITY = 60
        private const val PROFILE_PICTURE_MAX_WIDTH = 400
    }
}
