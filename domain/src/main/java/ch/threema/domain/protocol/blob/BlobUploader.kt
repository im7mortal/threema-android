package ch.threema.domain.protocol.blob

import ch.threema.base.ProgressListener
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.Http
import ch.threema.common.buildRequest
import ch.threema.common.execute
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.common.toHexString
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.data.leBytes
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import kotlin.concurrent.Volatile
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import okio.source

private val logger = getThreemaLogger("BlobUploader")

private const val PROGRESS_UPDATE_STEP_SIZE = 10

/**
 * Helper class that uploads a blob (image, video) to the blob server and returns the assigned blob
 * ID. No processing is done on the data; any encryption must happen separately.
 *
 * It can target both the default blob server and the mirror blob server for multi-device sessions.
 *
 * TODO(ANDR-2869): Rework exception handling (and maybe return types)
 */
class BlobUploader private constructor(
    private val okHttpClient: OkHttpClient,
    private val blobInputStream: InputStream,
    private val blobLength: Int,
    private val useMirror: Boolean,
    private val serverAddressProvider: ServerAddressProvider,
    @JvmField var progressListener: ProgressListener?,
    private val shouldPersist: Boolean,
    // used for mirror blob server request:
    private val multiDevicePropertyProvider: MultiDevicePropertyProvider?,
    private val blobScope: BlobScope?,
) {
    @Volatile
    private var isCancelled = false

    /**
     * Upload the given blob and return the blob ID on success.
     *
     * @return blob ID as a byte array or `null` if canceled via `cancel()`
     */
    @Throws(IOException::class, ThreemaException::class)
    fun upload(): ByteArray? {
        isCancelled = false

        val blobUploadUrl = getBlobUploadUrl()

        val uploadRequest = buildRequest {
            url(blobUploadUrl)
            header(Http.Header.CONTENT_TYPE, "multipart/form-data; boundary=$MULTIPART_BOUNDARY")
            post(buildRequestBody())
        }

        logger.info("Uploading blob ({} bytes) in scope {}", blobLength, blobScope)

        try {
            okHttpClient.execute(uploadRequest).use { response ->
                if (isCancelled) {
                    progressListener?.onFinished(false)
                    return null
                }

                val blobIdHex = response.getSuccessBodyOrThrow().string()
                progressListener?.onFinished(true)

                logger.info("Blob upload completed. ID = $blobIdHex")
                return blobIdHex.hexToByteArray()
            }
        } catch (e: IOException) {
            // Mutable field `isCancelled` will be mutated by calls to progressListener.onFinished
            val isCancelledAtTimeOfException = isCancelled
            progressListener?.onFinished(false)
            // If the "UploadBlobRequestBody" stops writing bytes due to cancellation, it results
            // in an IOException. But only catch it, if we cancelled this uploader on our own
            if (isCancelledAtTimeOfException) {
                logger.info("Blob upload cancelled manually")
                return null
            } else {
                logger.error("Blob upload failed (isCancelled: false)", e)
                throw e
            }
        } finally {
            blobInputStream.closeQuietly()
        }
    }

    @Throws(ThreemaException::class)
    private fun getBlobUploadUrl(): URL {
        if (useMirror) {
            if (multiDevicePropertyProvider == null) {
                throw ThreemaException("Missing parameter multiDevicePropertyProvider")
            }
            if (blobScope == null) {
                throw ThreemaException("Missing parameter blobScope")
            }
            val blobMirrorServerUploadUrl: String =
                serverAddressProvider.getBlobMirrorServerUploadUrl(multiDevicePropertyProvider)
            return URL(
                appendQueryParametersForMirrorServer(
                    rawUrl = blobMirrorServerUploadUrl,
                    multiDevicePropertyProvider = multiDevicePropertyProvider,
                    scope = blobScope,
                ),
            )
        } else {
            val blobServerUploadUrl: String = serverAddressProvider.getBlobServerUploadUrl()
            return URL(appendQueryParametersForUsualServer(blobServerUploadUrl))
        }
    }

    /**
     * @param rawUrl A url string **without** any query parameters. The value of this will not be mutated.
     */
    private fun appendQueryParametersForUsualServer(rawUrl: String): String =
        "$rawUrl?${getPersistParam()}"

    private fun getPersistParam(): String =
        "persist=${if (shouldPersist) 1 else 0}"

    /**
     * @param rawUrl An url string **without** any query parameters. The value of this will not be mutated.
     */
    @Throws(ThreemaException::class)
    private fun appendQueryParametersForMirrorServer(
        rawUrl: String,
        multiDevicePropertyProvider: MultiDevicePropertyProvider,
        scope: BlobScope,
    ): String {
        val deviceGroupIdHex = multiDevicePropertyProvider.get().keys.dgid.toHexString()
        val deviceIdHex = multiDevicePropertyProvider.get().mediatorDeviceId.leBytes().toHexString()
        return "$rawUrl?deviceId=$deviceIdHex&deviceGroupId=$deviceGroupIdHex&scope=${scope.name}&${getPersistParam()}"
    }

    private fun buildRequestBody(): RequestBody {
        val header =
            "--$MULTIPART_BOUNDARY\r\nContent-Disposition: form-data; name=\"blob\"; filename=\"blob.bin\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
        val footer = "\r\n--$MULTIPART_BOUNDARY--\r\n"
        return UploadBlobRequestBody(
            blobInputStream = blobInputStream,
            blobLength = blobLength.toLong(),
            bodyHeaderBytes = header.toByteArray(),
            bodyFooterBytes = footer.toByteArray(),
        )
    }

    /**
     * Send a cancel signal. If an upload is currently in progress, it will stop and `upload()` will return `null` immediately.
     */
    fun cancel() {
        this.isCancelled = true
    }

    private inner class UploadBlobRequestBody(
        private val blobInputStream: InputStream,
        private val blobLength: Long,
        private val bodyHeaderBytes: ByteArray,
        private val bodyFooterBytes: ByteArray,
    ) : RequestBody() {
        override fun contentType(): MediaType =
            ("multipart/form-data; boundary=$MULTIPART_BOUNDARY").toMediaType()

        override fun contentLength(): Long =
            bodyHeaderBytes.size + blobLength + bodyFooterBytes.size

        override fun isOneShot(): Boolean = true

        @Throws(IOException::class)
        override fun writeTo(sink: BufferedSink) {
            if (!isCancelled) {
                sink.write(bodyHeaderBytes)
            }
            blobInputStream.source().use { source ->
                // The rounded progress that is used for triggering the listeners
                var roundedProgress = 0
                // Actual progress
                var progress = 0L
                var read: Long = -1L
                while (
                    !isCancelled &&
                    (source.read(sink.buffer, 2048L).also { read = it }) != -1L
                ) {
                    progress += read
                    sink.flush()
                    // Compute rounded progress in percent
                    val newRoundedProgress = (100 * (progress.toDouble() / blobLength)).toInt()
                    // Only trigger listener if there is an update
                    if (
                        newRoundedProgress - roundedProgress >= PROGRESS_UPDATE_STEP_SIZE ||
                        newRoundedProgress == 100
                    ) {
                        progressListener?.updateProgress(newRoundedProgress)
                        roundedProgress = newRoundedProgress
                    }
                }
            }
            if (!isCancelled) {
                sink.write(bodyFooterBytes)
            }
        }
    }

    companion object {
        private const val MULTIPART_BOUNDARY = "---------------------------Boundary_Line"

        /**
         * Use this constructor when multi-device is currently **not** active on the device.
         * Use [mirror] otherwise.
         */
        @JvmStatic
        fun usual(
            okHttpClient: OkHttpClient,
            blobData: ByteArray,
            serverAddressProvider: ServerAddressProvider,
            progressListener: ProgressListener?,
            shouldPersist: Boolean,
        ): BlobUploader = BlobUploader(
            okHttpClient = okHttpClient,
            blobInputStream = ByteArrayInputStream(blobData),
            blobLength = blobData.size,
            useMirror = false,
            serverAddressProvider = serverAddressProvider,
            progressListener = progressListener,
            shouldPersist = shouldPersist,
            multiDevicePropertyProvider = null,
            blobScope = null,
        )

        /**
         * Use this constructor when multi-device is currently active on the device.
         * Use [usual] otherwise.
         */
        @JvmStatic
        fun mirror(
            okHttpClient: OkHttpClient,
            blobData: ByteArray,
            serverAddressProvider: ServerAddressProvider,
            progressListener: ProgressListener?,
            shouldPersist: Boolean,
            multiDevicePropertyProvider: MultiDevicePropertyProvider,
            blobScope: BlobScope,
        ): BlobUploader = BlobUploader(
            okHttpClient = okHttpClient,
            blobInputStream = ByteArrayInputStream(blobData),
            blobLength = blobData.size,
            useMirror = true,
            serverAddressProvider = serverAddressProvider,
            shouldPersist = shouldPersist,
            progressListener = progressListener,
            multiDevicePropertyProvider = multiDevicePropertyProvider,
            blobScope = blobScope,
        )
    }
}
