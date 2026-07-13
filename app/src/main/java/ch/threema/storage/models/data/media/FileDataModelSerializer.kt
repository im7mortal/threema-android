package ch.threema.storage.models.data.media

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.JsonArrayIterator
import ch.threema.common.addObject
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.logging.logAndReportError
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray

private val logger = getThreemaLogger("FileDataModelSerializer")

// TODO(ANDR-4958): Investigate JSON array tech-debt
object FileDataModelSerializer {
    @JvmStatic
    fun serializeFileDataBody(
        blobId: ByteArray?,
        encryptionKey: ByteArray?,
        mimeType: String? = null,
        fileSize: Long = 0,
        fileName: String? = null,
        renderingType: Int = FileData.RENDERING_DEFAULT,
        isDownloaded: Boolean = false,
        caption: String? = null,
        thumbnailMimeType: String? = null,
        metaData: Map<String, Any?>? = null,
    ): String =
        buildJsonArray {
            add(blobId?.toHexString())
            add(encryptionKey?.toHexString())
            add(mimeType)
            add(fileSize)
            add(fileName)
            add(renderingType)
            add(isDownloaded)
            add(caption)
            add(thumbnailMimeType)
            if (metaData != null) {
                addObject(metaData)
            } else {
                addJsonObject { }
            }
        }
            .toString()

    @JvmStatic
    fun deserializeFileDataBody(body: String): FileDataModel? {
        try {
            val iterator = JsonArrayIterator(body)
            val blobId = iterator.nextString()?.hexToByteArray()
            val encryptionKey = iterator.nextString()?.hexToByteArray()
            val mimeType = iterator.nextString()
            val fileSize = iterator.nextInt().toLong()
            val fileName = iterator.nextString()
            val renderingType = try {
                iterator.nextInt()
            } catch (_: IllegalArgumentException) {
                // very old models might not have a rendering type
                FileData.RENDERING_DEFAULT
            }
            val isDownloaded = iterator.nextBoolean()
            val caption = iterator.nextString()
            val thumbnailMimeType = try {
                iterator.nextString()
            } catch (_: NoSuchElementException) {
                // Since the thumbnail-mime-type field was added at a later point, some serialized file message bodies might exist that don't even
                // have "null" as a value in this JSON array. So we have to map a NoSuchElementException to "null" at this iterator position
                null
            }
            val metaData = try {
                iterator.nextPrimitiveValueMap()
            } catch (_: NoSuchElementException) {
                // Since the metadata field was added at a later point, some serialized file message bodies might exist that don't even
                // have "null" or "{}" as a value in this JSON array. So we have to map a NoSuchElementException to "null" at this iterator position
                null
            }
            return FileDataModel(
                blobId,
                encryptionKey,
                mimeType,
                thumbnailMimeType,
                fileSize,
                fileName,
                renderingType,
                caption,
                isDownloaded,
                metaData,
            )
        } catch (e: Exception) {
            logger.logAndReportError("Failed to deserialize file data body", e)
            return null
        }
    }
}
