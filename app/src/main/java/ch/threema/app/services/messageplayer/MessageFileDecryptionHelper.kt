package ch.threema.app.services.messageplayer

import androidx.annotation.MainThread
import ch.threema.app.services.FileService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.storage.models.MessageModel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("MessageFileDecryptionHelper")

class MessageFileDecryptionHelper(
    private val fileService: FileService,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val coroutineScope = CoroutineScope(dispatcherProvider.main.immediate)

    interface Callback {
        @MainThread
        fun onStart()

        @MainThread
        fun onDone(decryptedFile: File?)

        @MainThread
        fun onCancelled()
    }

    fun decrypt(message: MessageModel, callback: Callback): Job = coroutineScope.launch {
        callback.onStart()

        val decryptedFile = try {
            withContext(dispatcherProvider.io) {
                logger.debug("decrypting in background, message.id={}", message.id)
                try {
                    fileService.decryptMessageFileToShareableTempFile(message)
                } catch (e: Exception) {
                    logger.error("Failed to decrypt message file", e)
                    null
                }
            }
        } catch (e: CancellationException) {
            callback.onCancelled()
            throw e
        }
        callback.onDone(decryptedFile)
    }
}
