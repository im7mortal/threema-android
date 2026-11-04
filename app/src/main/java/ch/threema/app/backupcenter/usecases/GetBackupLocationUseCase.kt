package ch.threema.app.backupcenter.usecases

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import ch.threema.android.toTreeUriDocumentFile
import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.withContext

class GetBackupLocationUseCase(
    private val appContext: Context,
    private val preferenceService: PreferenceService,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(): Uri = withContext(dispatcherProvider.io) {
        preferenceService.getLocalDataBackupLocation()
            ?.takeIf { it.toTreeUriDocumentFile(appContext)?.isDirectory == true }
            ?: getDefaultLocation()
    }

    private fun getDefaultLocation(): Uri {
        val documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        documentsDirectory.mkdirs()
        return documentsDirectory.toUri()
    }
}
