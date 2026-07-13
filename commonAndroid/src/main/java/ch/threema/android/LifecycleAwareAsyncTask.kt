package ch.threema.android

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This class can be used as a drop-in replacement (albeit simplified a bit) for [android.os.AsyncTask].
 * It behaves essentially the same as [android.os.AsyncTask] but forces the caller to provide a lifecycle owner, to which the task is bound.
 * [onPreExecute], [doInBackground], and [onPostExecute] are only executed if the lifecycle is still active at that point and the task was
 * not canceled, otherwise the task ends.
 *
 * Do **not** use this class in new code. It is meant only as a transition step away from [android.os.AsyncTask].
 */
@Deprecated("Use coroutines directly")
abstract class LifecycleAwareAsyncTask<Params, Result> {

    private var coroutineScope: CoroutineScope? = null
    private var job: Job? = null

    @MainThread
    protected open fun onPreExecute() {
    }

    @WorkerThread
    protected abstract fun doInBackground(params: Params): Result

    @MainThread
    protected open fun onPostExecute(result: Result) {
    }

    protected fun publishProgress(block: Runnable) {
        coroutineScope?.launch {
            block.run()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun execute(lifecycleOwner: LifecycleOwner, params: Params) {
        cancel()
        coroutineScope = lifecycleOwner.lifecycleScope
        job = coroutineScope?.launch {
            onPreExecute()
            val result = withContext(DispatcherProvider.default.worker) {
                doInBackground(params)
            }
            onPostExecute(result)
            coroutineScope = null
        }
    }
}
