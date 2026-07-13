package ch.threema.app.usecases

import android.content.Context
import android.content.Intent
import ch.threema.app.R
import ch.threema.app.utils.FileProviderUtil
import ch.threema.common.DispatcherProvider
import java.io.File
import kotlinx.coroutines.withContext

class ShareDebugLogUseCase(
    private val appContext: Context,
    private val exportDebugLogUseCase: ExportDebugLogUseCase,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call() {
        val zipFile = exportDebugLogUseCase.call()
        withContext(dispatcherProvider.main) {
            shareFile(appContext, zipFile)
        }
    }

    private fun shareFile(context: Context, file: File) {
        val fileUri = FileProviderUtil.getUriForFile(context, file, FILE_NAME)
        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType(MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, fileUri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(createChooser(context, shareIntent), null)
    }

    private fun createChooser(context: Context, intent: Intent): Intent =
        Intent.createChooser(intent, context.getString(R.string.share_via))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        private const val FILE_NAME = "debug_log.zip"
        private const val MIME_TYPE = "application/zip"
    }
}
