package ch.threema.app.backuprestore

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.JsonArrayIterator
import ch.threema.common.takeUnlessEmpty
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.storage.models.data.media.FileDataModel
import ch.threema.storage.models.data.media.FileDataModelSerializer

private val logger = getThreemaLogger("LegacyMessageBodyTransformer")

object LegacyMessageBodyTransformer {
    @JvmStatic
    fun transformImageBodyToFileBody(body: String): String? {
        try {
            val iterator = JsonArrayIterator(body)
            val isDownloaded = iterator.nextBoolean()
            val encryptionKey = iterator.nextString()?.hexToByteArray()
            val blobId = iterator.nextString()?.hexToByteArray()
            val nonce = iterator.nextString()
            return FileDataModelSerializer.serializeFileDataBody(
                mimeType = "image/jpeg",
                blobId = blobId,
                encryptionKey = encryptionKey,
                isDownloaded = isDownloaded,
                renderingType = FileData.RENDERING_MEDIA,
                metaData = nonce?.takeUnlessEmpty()?.let {
                    mapOf(
                        FileDataModel.METADATA_KEY_LEGACY_NONCE to nonce,
                    )
                },
            )
        } catch (e: Exception) {
            logger.error("Failed to transform image body", e)
            return null
        }
    }

    @JvmStatic
    fun transformAudioBodyToFileBody(body: String): String? {
        try {
            val iterator = JsonArrayIterator(body)
            val duration = iterator.nextInt()
            val isDownloaded = iterator.nextBoolean()
            val encryptionKey = iterator.nextString()?.hexToByteArray()
            val blobId = iterator.nextString()?.hexToByteArray()
            return FileDataModelSerializer.serializeFileDataBody(
                mimeType = "audio/aac",
                blobId = blobId,
                encryptionKey = encryptionKey,
                isDownloaded = isDownloaded,
                renderingType = FileData.RENDERING_MEDIA,
                metaData = mapOf(
                    FileDataModel.METADATA_KEY_DURATION to duration,
                ),
            )
        } catch (e: Exception) {
            logger.error("Failed to transform audio body", e)
            return null
        }
    }

    @JvmStatic
    fun transformVideoBodyToFileBody(body: String): String? {
        try {
            val iterator = JsonArrayIterator(body)
            val duration = iterator.nextInt()
            val isDownloaded = iterator.nextBoolean()
            val encryptionKey = iterator.nextString()?.hexToByteArray()
            val blobId = iterator.nextString()?.hexToByteArray()
            val fileSize = if (iterator.hasNext()) {
                iterator.nextInt().toLong()
            } else {
                0L
            }
            return FileDataModelSerializer.serializeFileDataBody(
                mimeType = "video/mpeg",
                blobId = blobId,
                encryptionKey = encryptionKey,
                isDownloaded = isDownloaded,
                renderingType = FileData.RENDERING_MEDIA,
                fileSize = fileSize,
                metaData = mapOf(
                    FileDataModel.METADATA_KEY_DURATION to duration,
                ),
            )
        } catch (e: Exception) {
            logger.error("Failed to transform audio body", e)
            return null
        }
    }
}
