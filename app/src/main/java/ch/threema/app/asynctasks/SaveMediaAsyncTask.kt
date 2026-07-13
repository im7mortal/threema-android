package ch.threema.app.asynctasks

import android.view.View
import androidx.annotation.MainThread
import androidx.fragment.app.FragmentActivity
import ch.threema.android.LifecycleAwareAsyncTask
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.services.FileService
import ch.threema.app.utils.DialogUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.storage.models.AbstractMessageModel
import com.google.android.material.snackbar.Snackbar
import java.lang.ref.WeakReference

private val logger = getThreemaLogger("SaveMediaAsyncTask")

@Deprecated("Callers should directly use FileService, and handle the UI themselves")
class SaveMediaAsyncTask(
    private val fileService: FileService,
    activity: FragmentActivity,
    feedbackView: View?,
    selectedMessages: List<AbstractMessageModel>,
    val quiet: Boolean,
) : LifecycleAwareAsyncTask<Unit, Int>() {

    private val activityWeakReference = WeakReference(activity)
    private val feedbackViewWeakReference = WeakReference<View?>(feedbackView)
    private val selectedMessages = selectedMessages.toList()

    private var cancelled = false

    override fun onPreExecute() {
        val selectedMessagesCount = selectedMessages.size
        val activity = activityWeakReference.get()
        if (activity == null || selectedMessagesCount < 4) {
            return
        }
        val title = activity.resources.getQuantityString(R.plurals.saving_media, selectedMessagesCount, selectedMessagesCount)
        val cancel = activity.getString(R.string.cancel)
        val dialog = CancelableHorizontalProgressDialog.newInstance(title, cancel, selectedMessagesCount)
        dialog.setOnCancelListener { _, _ ->
            cancelled = true
        }
        dialog.show(activity.supportFragmentManager, DIALOG_TAG_SAVING_MEDIA)
    }

    override fun doInBackground(params: Unit): Int {
        var i = 0
        var saved = 0
        val checkedItemsIterator = selectedMessages.iterator()
        while (checkedItemsIterator.hasNext() && !cancelled) {
            val progress = i++
            publishProgress { onProgressUpdate(progress + 1) }
            val messageModel = checkedItemsIterator.next()
            try {
                fileService.copyMediaFileToGallery(messageModel)
                saved++
                logger.debug("Saved message {}", messageModel.uid)
            } catch (e: Exception) {
                logger.error("Failed to save media", e)
                activityWeakReference.get()?.let { activity ->
                    val message = activity.getString(R.string.error_saving_file)
                    if (quiet) {
                        activity.showToast(message)
                    } else if (!activity.isFinishing) {
                        SimpleStringAlertDialog.newInstance(R.string.whoaaa, message).show(activity.supportFragmentManager, "tex")
                    }
                }
            }
        }
        return saved
    }

    override fun onPostExecute(result: Int) {
        val activity = activityWeakReference.get() ?: return
        DialogUtil.dismissDialog(activity.supportFragmentManager, DIALOG_TAG_SAVING_MEDIA, true)
        val feedbackView = feedbackViewWeakReference.get()
        val message = activity.resources.getQuantityString(R.plurals.file_saved, result, result)
        if (feedbackView != null) {
            Snackbar.make(feedbackView, message, Snackbar.LENGTH_SHORT).show()
        } else {
            activity.showToast(message)
        }
    }

    @MainThread
    private fun onProgressUpdate(progress: Int) {
        val activity = activityWeakReference.get() ?: return
        DialogUtil.updateProgress(activity.supportFragmentManager, DIALOG_TAG_SAVING_MEDIA, progress)
    }

    fun execute() {
        activityWeakReference.get()?.let { lifecycleOwner ->
            execute(lifecycleOwner, Unit)
        }
    }

    companion object {
        private const val DIALOG_TAG_SAVING_MEDIA = "savingToGallery"
    }
}
