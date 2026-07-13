package ch.threema.app.services

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.WorkerThread
import ch.threema.app.cache.ThumbnailCache
import ch.threema.app.utils.ResettableInputStream
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.ContactModel
import ch.threema.data.models.GroupModel
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.IdentityString
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.AbstractMessageModel
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

interface FileService {
    fun getDefaultBackupPath(): File

    /**
     * @return Uri of data backup path, or null if not yet selected by user
     */
    fun getBackupUri(): Uri?

    @Throws(IOException::class)
    @WorkerThread
    fun createTempFile(prefix: String, suffix: String?): File

    @Throws(IOException::class)
    @WorkerThread
    fun createShareableTempFile(prefix: String, suffix: String?): File

    fun hasUserDefinedProfilePicture(identity: IdentityString): Boolean

    fun hasContactDefinedProfilePicture(identity: IdentityString): Boolean

    fun hasAndroidDefinedProfilePicture(identity: IdentityString): Boolean

    fun hasMessageFile(messageUid: MessageUid): Boolean

    fun hasMessageThumbnail(messageUid: MessageUid): Boolean

    @WorkerThread
    fun deleteMessageFiles(messageUid: MessageUid, keepThumbnails: Boolean = false): Boolean

    /**
     * @return a decrypted file from a message, or null if the message or file does not exist
     * The file may *not* be shared with other apps.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun decryptMessageFileToTempFile(message: AbstractMessageModel): File?

    /**
     * @return a decrypted file from a message, or null if the message or file does not exist.
     * The file may be shared with other apps.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun decryptMessageFileToShareableTempFile(message: AbstractMessageModel): File?

    @Throws(IOException::class)
    fun decryptMessageFileToStream(messageUid: MessageUid): InputStream?

    @Throws(IOException::class)
    fun decryptedMessageThumbnailToStream(messageUid: MessageUid): InputStream?

    @Throws(IOException::class)
    @WorkerThread
    fun copyMediaFileToGallery(message: AbstractMessageModel)

    @Throws(IOException::class)
    @WorkerThread
    fun copyDecryptedFileToGallery(sourceUri: Uri, mimeType: String)

    /**
     * Create a media file for a specific message using the provided data, and return true on success.
     * Will fail if the file already exists.
     */
    @WorkerThread
    fun writeConversationMedia(messageUid: MessageUid, data: ByteArray, pos: Int, length: Int): Boolean =
        writeConversationMedia(messageUid, data, pos, length, false)

    /**
     * Create a media file for a specific message using the provided data, and return true on success.
     * If the file already exists, it will be overwritten if [overwrite] is true, otherwise it will fail.
     */
    @WorkerThread
    fun writeConversationMedia(messageUid: MessageUid, data: ByteArray, pos: Int, length: Int, overwrite: Boolean): Boolean =
        writeConversationMedia(messageUid, ByteArrayInputStream(data, pos, length), false)

    /**
     * Create a media file for a specific message using the provided input stream, and return true on success.
     * If the file already exists, it will be overwritten if [overwrite] is true, otherwise it will fail.
     */
    @WorkerThread
    fun writeConversationMedia(messageUid: MessageUid, inputStream: InputStream, overwrite: Boolean): Boolean

    /**
     * Save a group profile picture. Additionally, this resets the avatar cache for this group.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun writeGroupProfilePicture(groupModel: GroupModel, data: ByteArray) {
        writeGroupProfilePicture(groupModel, ByteArrayInputStream(data))
    }

    /**
     * Save a group profile picture. Additionally, this resets the avatar cache for this group.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun writeGroupProfilePicture(groupModel: GroupModel, data: InputStream) {
        writeGroupProfilePicture(groupModel.groupIdentity, groupModel.getDatabaseId(), data)
    }

    @Throws(IOException::class)
    @WorkerThread
    fun writeGroupProfilePicture(groupIdentity: GroupIdentity, groupDatabaseId: GroupDatabaseId, data: InputStream)

    /**
     * @return the group profile picture as InputStream, or null if there is no group profile picture
     */
    @Throws(IOException::class)
    fun getGroupProfilePictureStream(groupDatabaseId: GroupDatabaseId): InputStream?

    /**
     * @return the group profile picture as byte array, or null if there is no group profile picture
     */
    @Throws(IOException::class)
    fun getGroupProfilePictureBytes(groupDatabaseId: GroupDatabaseId): ByteArray?

    /**
     * @return the group profile picture, or null if there is no group profile picture or the file does not exist or is not a valid bitmap
     */
    fun getGroupProfilePictureBitmap(groupDatabaseId: GroupDatabaseId): Bitmap?

    @WorkerThread
    fun removeGroupProfilePicture(groupIdentity: GroupIdentity, groupDatabaseId: GroupDatabaseId)

    /**
     * Remove the group profile picture. Additionally, this resets the avatar cache for this group.
     */
    @WorkerThread
    fun removeGroupProfilePicture(groupModel: GroupModel) {
        removeGroupProfilePicture(groupModel.groupIdentity, groupModel.getDatabaseId())
    }

    fun hasGroupProfilePicture(groupDatabaseId: GroupDatabaseId): Boolean

    /**
     * Write the contact profile picture set by the user. Additionally, this resets the avatar cache
     * for this contact.
     */
    @WorkerThread
    fun writeUserDefinedProfilePicture(identity: IdentityString, file: File): Boolean

    /**
     * Write the contact profile picture set by the user. Additionally, this resets the avatar cache
     * for this contact.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun writeUserDefinedProfilePicture(identity: IdentityString, file: ByteArray) {
        writeUserDefinedProfilePicture(identity, ByteArrayInputStream(file))
    }

    /**
     * Write the contact profile picture set by the user. Additionally, this resets the avatar cache
     * for this contact.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun writeUserDefinedProfilePicture(identity: IdentityString, inputStream: InputStream)

    /**
     * Write the contact profile picture received by the contact. Additionally, this resets the
     * avatar cache for this contact.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun writeContactDefinedProfilePicture(identity: IdentityString, imageData: ByteArray) {
        writeContactDefinedProfilePicture(identity, ByteArrayInputStream(imageData))
    }

    /**
     * Write the contact profile picture received by the contact. Additionally, this resets the
     * avatar cache for this contact.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun writeContactDefinedProfilePicture(identity: IdentityString, imageData: InputStream)

    /**
     * Write the contact profile picture from Android's address book. Additionally, this resets the
     * avatar cache for this contact.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun writeAndroidDefinedProfilePicture(identity: IdentityString, imageData: ByteArray)

    /**
     * @return the decrypted bitmap of a contact profile picture, or null if no file exists
     */
    @WorkerThread
    fun getUserDefinedProfilePicture(identity: IdentityString): Bitmap?

    @WorkerThread
    fun getAndroidDefinedProfilePicture(contactModel: ContactModel): Bitmap?

    /**
     * Return an input stream of a local saved contact profile picture
     */
    @Throws(IOException::class)
    fun getUserDefinedProfilePictureStream(identity: IdentityString): InputStream?

    /**
     * @return an input stream of a contact photo, or null if no contact defined profile picture exists
     */
    @Throws(IOException::class)
    fun getContactDefinedProfilePictureStream(identity: IdentityString): InputStream?

    /**
     * @return the decrypted bitmap of a contact-provided profile picture, or null if no contact defined profile picture exists
     */
    @WorkerThread
    fun getContactDefinedProfilePicture(identity: IdentityString): Bitmap?

    /**
     * Remove the user defined profile picture for the contact with the given identity.
     * Additionally, this resets the avatar cache for this contact.
     *
     * @param identity the identity of the contact
     * @return true if the profile picture was deleted, false if the remove failed or no profile picture file exists
     */
    @WorkerThread
    fun removeUserDefinedProfilePicture(identity: IdentityString): Boolean

    /**
     * Remove the contact defined profile picture for the contact with the given identity.
     * Additionally, this resets the avatar cache for this contact.
     *
     * @param identity the identity of the contact
     */
    @WorkerThread
    fun removeContactDefinedProfilePicture(identity: IdentityString)

    /**
     * Remove the profile picture from Android's address book. Additionally, this resets the avatar
     * cache for this contact.
     *
     * @return true if the profile picture was deleted, false if the remove failed or no profile picture file exists
     */
    @WorkerThread
    fun removeAndroidDefinedProfilePicture(identity: IdentityString): Boolean

    @Throws(IOException::class)
    @WorkerThread
    fun saveThumbnail(messageUid: MessageUid, thumbnail: InputStream)

    @Throws(IOException::class)
    @WorkerThread
    fun writeConversationMediaThumbnail(message: AbstractMessageModel, thumbnail: ByteArray) {
        writeConversationMediaThumbnail(
            message,
            ResettableInputStream { ByteArrayInputStream(thumbnail) },
        )
    }

    @Throws(IOException::class)
    @WorkerThread
    fun writeConversationMediaThumbnail(message: AbstractMessageModel, thumbnail: ResettableInputStream)

    @WorkerThread
    fun getMessageThumbnailBitmap(message: AbstractMessageModel?, thumbnailCache: ThumbnailCache<Int>? = null): Bitmap?

    @WorkerThread
    fun deleteMediaFiles()

    @WorkerThread
    fun copyUriToTempFile(source: Uri, prefix: String, suffix: String?): File?

    /**
     * Copies the already-decrypted file of a file message into the 'share' directory, such that
     * it can be shared with other apps.
     */
    @WorkerThread
    fun copyDecryptedMessageFileToShareDirectory(message: AbstractMessageModel, decryptedFile: File): Uri?

    /**
     * Decrypt the files of [messages].
     * The returned list may not contain all files, in case some fail to be decrypted.
     */
    @WorkerThread
    fun decryptMessageFiles(messages: List<AbstractMessageModel>): List<File>

    /**
     * Decrypt the files of [messages] into the 'share' directory, such that they can then be shared.
     * The returned list may not contain all files, in case some fail to be decrypted.
     */
    @WorkerThread
    fun decryptMessageFilesForSharing(messages: List<AbstractMessageModel>): List<Uri>

    /**
     * Copy the decrypted thumbnail to a temporary file accessible through our FileProvider and return the Uri of the temporary file
     *
     * @param message Message Model used as the source for the thumbnail
     * @param maxSize      Maximum size of the thumbnail in bytes. Set to Integer.MAX_VALUE if no limit
     * @return Uri of the temporary file, or null if the thumbnail does not exist, is too large or an error occurred
     */
    @WorkerThread
    fun decryptThumbnailToShareableTempFile(message: AbstractMessageModel, maxSize: Int): Uri?
}
