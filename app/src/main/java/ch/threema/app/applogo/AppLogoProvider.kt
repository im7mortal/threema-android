package ch.threema.app.applogo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ch.threema.app.files.AppLogoFileHandleProvider
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.TimeProvider
import ch.threema.common.copyTo
import ch.threema.common.files.FileHandle
import ch.threema.common.plus
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("AppLogoProvider")

class AppLogoProvider(
    private val preferenceService: PreferenceService,
    private val appLogoFileHandleProvider: AppLogoFileHandleProvider,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val logoChanged = MutableSharedFlow<Unit>()

    fun isAppLogoExpired(theme: AppLogoFileHandleProvider.Theme): Boolean {
        val expiresAt = preferenceService.getAppLogoExpiresAt(theme)
        return expiresAt == null || timeProvider.get() > expiresAt
    }

    fun watchAppLogoBitmap(theme: AppLogoFileHandleProvider.Theme): Flow<Bitmap?> = callbackFlow {
        send(getAppLogo(theme))
        logoChanged.collectLatest {
            send(getAppLogo(theme))
        }
    }

    private suspend fun getAppLogo(theme: AppLogoFileHandleProvider.Theme): Bitmap? =
        withContext(dispatcherProvider.io) {
            try {
                getFileHandle(theme).read()?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: IOException) {
                logger.error("Failed to load app logo bitmap ({})", theme, e)
                null
            }
        }

    @Throws(IOException::class)
    suspend fun saveAppLogo(file: File, theme: AppLogoFileHandleProvider.Theme, expires: Instant?) = withContext(dispatcherProvider.io) {
        file.copyTo(getFileHandle(theme))
        preferenceService.setAppLogoExpiresAt(expires ?: (timeProvider.get() + 1.days), theme)
        logoChanged.emit(Unit)
    }

    @Throws(IOException::class)
    suspend fun clearAppLogo(theme: AppLogoFileHandleProvider.Theme) = withContext(dispatcherProvider.io) {
        getFileHandle(theme).delete()
        preferenceService.setAppLogoExpiresAt(null, theme)
        logoChanged.emit(Unit)
    }

    private fun getFileHandle(theme: AppLogoFileHandleProvider.Theme): FileHandle =
        appLogoFileHandleProvider.get(theme)
}
