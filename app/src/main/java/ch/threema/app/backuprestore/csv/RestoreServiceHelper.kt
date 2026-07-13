package ch.threema.app.backuprestore.csv

import android.content.Context
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.reset.ResetAppTask
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("RestoreServiceHelper")

/**
 * This class serves as an extension to [RestoreService], primarily to allow the use of Kotlin-only features such as coroutines.
 */
class RestoreServiceHelper(
    private val appContext: Context,
    private val resetAppTask: ResetAppTask,
    private val dispatcherProvider: DispatcherProvider,
) {
    fun resetAppAsync() {
        CoroutineScope(dispatcherProvider.worker).launch {
            execute()
        }
    }

    private suspend fun execute() {
        try {
            resetAppTask.execute(clearAllAppUserData = false)
        } catch (e: Exception) {
            logger.error("Failed to reset app", e)
            appContext.showToast(R.string.an_error_occurred, ToastDuration.LONG)

            // Deletion may have partially succeeded, so the app may be in an inconsistent state now.
            // Therefore, it is safer to close the app entirely.
            exitProcess(0)
        }
    }
}
