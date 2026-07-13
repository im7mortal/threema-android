package ch.threema.app.asynctasks

import androidx.annotation.WorkerThread
import ch.threema.android.LifecycleAwareAsyncTask
import ch.threema.app.services.FileService
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageType
import java.io.File

@Deprecated("Use coroutines instead")
open class LoadDecryptedMessageFileAsyncTask(
    private val fileService: FileService,
) : LifecycleAwareAsyncTask<AbstractMessageModel, Unit?>() {

    override fun doInBackground(message: AbstractMessageModel): Unit? {
        if (message.type == MessageType.TEXT || message.type == MessageType.POLL || message.type == MessageType.LOCATION) {
            onSuccess(null)
            return null
        }
        try {
            onSuccess(fileService.decryptMessageFileToShareableTempFile(message))
        } catch (e: Exception) {
            onError(e)
        }
        return null
    }

    @WorkerThread
    open fun onSuccess(file: File?) {}

    @WorkerThread
    open fun onError(e: Exception) {}
}
