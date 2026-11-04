package ch.threema.app.libthreema.extensions

import ch.threema.app.services.FileService
import ch.threema.data.models.GroupModel
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.ProfilePicture as LibthreemaProfilePicture
import ch.threema.libthreema.ProfilePictureMediaType as LibthreemaProfilePictureMediaType

fun FileService.getContactDefinedLibthreemaProfilePicture(identity: IdentityString): LibthreemaProfilePicture? =
    getContactDefinedProfilePictureStream(identity)?.use { inputStream ->
        LibthreemaProfilePicture(
            mediaType = LibthreemaProfilePictureMediaType.JPEG,
            data = inputStream.readBytes(),
        )
    }

fun FileService.getUserDefinedLibthreemaProfilePicture(identity: IdentityString): LibthreemaProfilePicture? =
    getUserDefinedProfilePictureStream(identity)?.use { inputStream ->
        LibthreemaProfilePicture(
            mediaType = LibthreemaProfilePictureMediaType.JPEG,
            data = inputStream.readBytes(),
        )
    }

fun FileService.getLibthreemaGroupProfilePicture(groupDatabaseId: GroupDatabaseId): LibthreemaProfilePicture? =
    getGroupProfilePictureBytes(groupDatabaseId)?.let { groupProfilePictureBytes ->
        LibthreemaProfilePicture(
            mediaType = LibthreemaProfilePictureMediaType.JPEG,
            data = groupProfilePictureBytes,
        )
    }

fun FileService.persistLibthreemaGroupProfilePicture(groupModel: GroupModel, profilePicture: LibthreemaProfilePicture?) {
    if (profilePicture != null) {
        writeGroupProfilePicture(groupModel, profilePicture.data)
    } else {
        removeGroupProfilePicture(groupModel)
    }
}
