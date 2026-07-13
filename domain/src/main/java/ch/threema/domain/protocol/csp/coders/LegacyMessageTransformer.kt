package ch.threema.domain.protocol.csp.coders

import ch.threema.base.crypto.NaCl
import ch.threema.common.readByteArray
import ch.threema.common.readLittleEndianInt
import ch.threema.common.readLittleEndianShort
import ch.threema.common.readUtf8String
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.domain.protocol.csp.messages.file.FileMessage
import ch.threema.domain.protocol.csp.messages.file.GroupFileMessage
import java.io.ByteArrayInputStream
import java.io.IOException

object LegacyMessageTransformer {
    private const val IMAGE_SIZE_INT_BYTE_LENGTH = 4
    private const val AUDIO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH = 2
    private const val AUDIO_SIZE_INT_BYTE_LENGTH = 4
    private const val VIDEO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH = 2
    private const val VIDEO_SIZE_INT_BYTE_LENGTH = 4
    private const val THUMBNAIL_SIZE_INT_BYTE_LENGTH = 4

    private const val IMAGE_FILE_DATA_LENGTH = ProtocolDefines.BLOB_ID_LEN +
        IMAGE_SIZE_INT_BYTE_LENGTH +
        NaCl.NONCE_BYTES
    private const val AUDIO_FILE_DATA_LENGTH = AUDIO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH +
        ProtocolDefines.BLOB_ID_LEN + AUDIO_SIZE_INT_BYTE_LENGTH +
        ProtocolDefines.BLOB_KEY_LEN
    private const val VIDEO_FILE_DATA_LENGTH = VIDEO_LENGTH_IN_SECONDS_SHORT_BYTE_LENGTH +
        ProtocolDefines.BLOB_ID_LEN + VIDEO_SIZE_INT_BYTE_LENGTH +
        ProtocolDefines.BLOB_ID_LEN + THUMBNAIL_SIZE_INT_BYTE_LENGTH +
        ProtocolDefines.BLOB_KEY_LEN

    private const val MIME_TYPE_JPEG = "image/jpeg"
    private const val MIME_TYPE_AAC = "audio/aac"
    private const val MIME_TYPE_MPEG = "video/mpeg"

    private const val META_DATA_KEY_DURATION = "d"
    private const val META_DATA_KEY_LEGACY_NONCE = "_legacy_nonce"

    /**
     * Parses the [data] of an image message and transforms it into a [FileMessage].
     *
     * The [data] byte array consists of:
     *  - image-blob-id bytes (length 16)
     *  - image-size int (length 4)
     *  - nonce bytes (length 24)
     *
     * @param data the data that represents the audio message
     * @param offset the offset where the actual data starts (inclusive)
     * @param length the length of the data (needed to ignore the padding)
     * @throws BadMessageException if the length or the offset is invalid
     */
    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformImageMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): FileMessage {
        if (length < IMAGE_FILE_DATA_LENGTH) {
            throw BadMessageException("Bad length ($length) for image message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }
        return parseData(data, offset, length) { bis ->
            val fileData = bis.readImageFileData()
            FileMessage().apply {
                this.fileData = fileData
            }
        }
    }

    private fun ByteArrayInputStream.readImageFileData(): FileData {
        val blobId = readByteArray(ProtocolDefines.BLOB_ID_LEN)
        val imageSize = readLittleEndianInt()
        val nonce = readByteArray(NaCl.NONCE_BYTES)
        return FileData().apply {
            setMimeType(MIME_TYPE_JPEG)
            setFileBlobId(blobId)
            setFileSize(imageSize.toLong())
            setRenderingType(FileData.RENDERING_MEDIA)

            // The nonce is required to decrypt the blob later, so we include it as a custom meta data value.
            setMetaData(mapOf(META_DATA_KEY_LEGACY_NONCE to nonce.toHexString()))
        }
    }

    /**
     * Parses the [data] of an audio message and transforms it into a [FileMessage].
     *
     * The [data] byte array consists of:
     *  - audio-duration short (length 2, in seconds)
     *  - audio-blob-id bytes (length 16)
     *  - audio-size int (length 4)
     *  - encryption-key bytes (length 32)
     *
     * @param data the data that represents the audio message
     * @param offset the offset where the actual data starts (inclusive)
     * @param length the length of the data (needed to ignore the padding)
     * @throws BadMessageException if the length or the offset is invalid
     */
    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformAudioMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): FileMessage {
        if (length < AUDIO_FILE_DATA_LENGTH) {
            throw BadMessageException("Bad length ($length) for audio message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }

        return parseData(data, offset, length) { bis ->
            val fileData = bis.readAudioFileData()
            FileMessage().apply {
                this.fileData = fileData
            }
        }
    }

    private fun ByteArrayInputStream.readAudioFileData(): FileData {
        val durationInSeconds = readLittleEndianShort()
        val audioBlobId = readByteArray(ProtocolDefines.BLOB_ID_LEN)
        val audioSizeInBytes = readLittleEndianInt()
        val encryptionKey = readByteArray(ProtocolDefines.BLOB_KEY_LEN)
        return FileData().apply {
            setMimeType(MIME_TYPE_AAC)
            setFileBlobId(audioBlobId)
            setFileSize(audioSizeInBytes.toLong())
            setRenderingType(FileData.RENDERING_MEDIA)
            setMetaData(mapOf(META_DATA_KEY_DURATION to durationInSeconds.toInt()))
            setEncryptionKey(encryptionKey)
        }
    }

    /**
     * Parses the [data] of a video message and transforms it into a [FileMessage].
     *
     * The [data] byte array consists of:
     *  - video-duration short (length 2, in seconds)
     *  - video-blob-id (length 16)
     *  - video-size int (length 4)
     *  - thumbnail-blob-id (length 16)
     *  - thumbnail-size int (length 4)
     *  - encryption-key bytes (length 32)
     *
     * @param data the data that represents the video message
     * @param offset the offset where the actual data starts (inclusive)
     * @param length the length of the data (needed to ignore the padding)
     * @throws BadMessageException if the length or the offset is invalid
     */
    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformVideoMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): FileMessage {
        if (length < VIDEO_FILE_DATA_LENGTH) {
            throw BadMessageException("Bad length ($length) for video message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }

        return parseData(data, offset, length) { bis ->
            val fileData = bis.readVideoFileData()
            FileMessage().apply {
                this.fileData = fileData
            }
        }
    }

    private fun ByteArrayInputStream.readVideoFileData(): FileData {
        val durationInSeconds = readLittleEndianShort()
        val videoBlobId = readByteArray(ProtocolDefines.BLOB_ID_LEN)
        val videoSizeInBytes = readLittleEndianInt()
        val thumbnailBlobId = readByteArray(ProtocolDefines.BLOB_ID_LEN)
        // Skip the thumbnail size
        readLittleEndianInt()
        val encryptionKey = readByteArray(ProtocolDefines.BLOB_KEY_LEN)
        return FileData().apply {
            setMimeType(MIME_TYPE_MPEG)
            setFileBlobId(videoBlobId)
            setFileSize(videoSizeInBytes.toLong())
            setThumbnailBlobId(thumbnailBlobId)
            setThumbnailMimeType(MIME_TYPE_JPEG)
            setRenderingType(FileData.RENDERING_MEDIA)
            setMetaData(mapOf(META_DATA_KEY_DURATION to durationInSeconds.toInt()))
            setEncryptionKey(encryptionKey)
        }
    }

    /**
     * Parses the [data] of a group image message and transforms it into a [GroupFileMessage].
     *
     * The [data] byte array consists of:
     *  - group creator identity string (length 8)
     *  - group id identity string (length 8)
     *  - image-blob-id bytes (length 16)
     *  - image-size int (length 4)
     *  - nonce bytes (length 24)
     *
     * @param data the data that represents the group image message
     * @param offset the offset where the actual data starts (inclusive)
     * @param length the length of the data (needed to ignore the padding)
     * @throws BadMessageException if the length or the offset is invalid
     */
    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformGroupImageMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): GroupFileMessage {
        val minLength = ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + IMAGE_FILE_DATA_LENGTH
        if (length < minLength) {
            throw BadMessageException("Bad length ($length) for group image message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }

        return parseData(data, offset, length) { bis ->
            val groupCreator = bis.readUtf8String(ProtocolDefines.IDENTITY_LEN)
            val groupId = GroupId(bis.readByteArray(ProtocolDefines.GROUP_ID_LEN))
            val fileData = bis.readGroupImageFileData()
            GroupFileMessage().apply {
                this.groupCreator = groupCreator
                this.apiGroupId = groupId
                this.fileData = fileData
            }
        }
    }

    private fun ByteArrayInputStream.readGroupImageFileData(): FileData {
        val imageBlobId = readByteArray(ProtocolDefines.BLOB_ID_LEN)
        val imageSizeInBytes = readLittleEndianInt()
        val encryptionKey = readByteArray(ProtocolDefines.BLOB_KEY_LEN)
        return FileData().apply {
            setMimeType(MIME_TYPE_JPEG)
            setFileBlobId(imageBlobId)
            setFileSize(imageSizeInBytes.toLong())
            setRenderingType(FileData.RENDERING_MEDIA)
            setEncryptionKey(encryptionKey)
        }
    }

    /**
     * Parses the [data] of a group audio message and transforms it into a [GroupFileMessage].
     *
     * The [data] byte array consists of:
     *  - group creator identity string (length 8)
     *  - group id identity string (length 8)
     *  - audio-duration short (length 2, in seconds)
     *  - audio-blob-id byte (length 16)
     *  - audio-size int (length 4)
     *  - encryption-key bytes (length 32)
     *
     * @param data the data that represents the group audio message
     * @param offset the offset where the actual data starts (inclusive)
     * @param length the length of the data (needed to ignore the padding)
     * @throws BadMessageException if the length or the offset is invalid
     */
    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformGroupAudioMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): GroupFileMessage {
        val minLength = ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + AUDIO_FILE_DATA_LENGTH
        if (length < minLength) {
            throw BadMessageException("Bad length ($length) for group audio message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }

        return parseData(data, offset, length) { bis ->
            val groupCreator = bis.readUtf8String(ProtocolDefines.IDENTITY_LEN)
            val groupId = GroupId(bis.readByteArray(ProtocolDefines.GROUP_ID_LEN))
            val fileData = bis.readAudioFileData()
            GroupFileMessage().apply {
                this.groupCreator = groupCreator
                this.apiGroupId = groupId
                this.fileData = fileData
            }
        }
    }

    /**
     * Parses the [data] of a group video message and transforms it into a [GroupFileMessage].
     *
     * The [data] byte array consists of:
     *  - group creator identity string (length 8)
     *  - group id identity string (length 8)
     *  - video-duration short (length 2, in seconds)
     *  - video-blob-id (length 16)
     *  - video-size int (length 4)
     *  - thumbnail-blob-id (length 16)
     *  - thumbnail-size int (length 4)
     *  - encryption-key bytes (length 32)
     *
     * @param data the data that represents the group video message
     * @param offset the offset where the actual data starts (inclusive)
     * @param length the length of the data (needed to ignore the padding)
     * @throws BadMessageException if the length or the offset is invalid
     */
    @JvmStatic
    @Throws(BadMessageException::class)
    fun transformGroupVideoMessage(data: ByteArray, offset: Int = 0, length: Int = data.size): GroupFileMessage {
        val minLength = ProtocolDefines.IDENTITY_LEN + ProtocolDefines.GROUP_ID_LEN + VIDEO_FILE_DATA_LENGTH
        if (length < minLength) {
            throw BadMessageException("Bad length ($length) for group video message")
        }
        if (data.size < length + offset) {
            throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
        }

        return parseData(data, offset, length) { bis ->
            val groupCreator = bis.readUtf8String(ProtocolDefines.IDENTITY_LEN)
            val groupId = GroupId(bis.readByteArray(ProtocolDefines.GROUP_ID_LEN))
            val fileData = bis.readVideoFileData()
            GroupFileMessage().apply {
                this.groupCreator = groupCreator
                this.apiGroupId = groupId
                this.fileData = fileData
            }
        }
    }

    private fun <T> parseData(data: ByteArray, offset: Int, length: Int, parse: (ByteArrayInputStream) -> T): T {
        val stream = ByteArrayInputStream(data, offset, length)
        try {
            return parse(stream)
        } catch (e: IOException) {
            throw BadMessageException("Message body contents failed to parse", e)
        }
    }
}
