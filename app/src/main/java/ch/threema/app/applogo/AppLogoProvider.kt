package ch.threema.app.applogo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ch.threema.app.files.AppLogoFileHandleProvider
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConfigUtils.AppThemeSetting
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

    fun isAppLogoExpired(@AppThemeSetting theme: String): Boolean {
        val expiresAt = preferenceService.getAppLogoExpiresAt(theme)
        return expiresAt == null || timeProvider.get() > expiresAt
    }

    fun watchAppLogoBitmap(@AppThemeSetting theme: String): Flow<Bitmap?> = callbackFlow {
        send(getAppLogo(theme))
        logoChanged.collectLatest {
            send(getAppLogo(theme))
        }
    }

    private suspend fun getAppLogo(@AppThemeSetting theme: String): Bitmap? =
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
    suspend fun saveAppLogo(file: File, @AppThemeSetting theme: String, expires: Instant?) = withContext(dispatcherProvider.io) {
        file.copyTo(getFileHandle(theme))
        preferenceService.setAppLogoExpiresAt(expires ?: (timeProvider.get() + 1.days), theme)
        logoChanged.emit(Unit)
    }

    @Throws(IOException::class)
    suspend fun clearAppLogo(@AppThemeSetting theme: String) = withContext(dispatcherProvider.io) {
        getFileHandle(theme).delete()
        preferenceService.setAppLogoExpiresAt(null, theme)
        logoChanged.emit(Unit)
    }

    private fun getFileHandle(@AppThemeSetting theme: String): FileHandle {
        val logoTheme = if (theme == ConfigUtils.THEME_DARK) {
            AppLogoFileHandleProvider.Theme.DARK
        } else {
            AppLogoFileHandleProvider.Theme.LIGHT
        }
        return appLogoFileHandleProvider.get(logoTheme)
    }
}
