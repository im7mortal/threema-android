package ch.threema.app.applogo

import android.provider.MediaStore
import ch.threema.app.services.FileService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConfigUtils.AppThemeSetting
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.Http
import ch.threema.common.buildRequest
import ch.threema.common.copyIntoFile
import ch.threema.common.executeAsync
import ch.threema.common.getExpiration
import ch.threema.common.getSuccessBodyOrThrow
import java.io.IOException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

private val logger = getThreemaLogger("UpdateAppLogoUseCase")

class UpdateAppLogoUseCase(
    private val fileService: FileService,
    private val appLogoProvider: AppLogoProvider,
    private val okHttpClient: OkHttpClient,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(
        lightUrl: String?,
        darkUrl: String?,
        forceUpdate: Boolean,
    ) {
        logger.debug("start update app logo {}, {}", lightUrl, darkUrl)
        updateLogo(lightUrl, ConfigUtils.THEME_LIGHT, forceUpdate)
        updateLogo(darkUrl, ConfigUtils.THEME_DARK, forceUpdate)
    }

    private suspend fun updateLogo(logoUrl: String?, @AppThemeSetting theme: String, forceUpdate: Boolean) = withContext(dispatcherProvider.io) {
        try {
            if (logoUrl.isNullOrEmpty()) {
                logger.info("Clearing app logo for (forcedUpdate={}, theme={})", forceUpdate, theme)
                appLogoProvider.clearAppLogo(theme)
                return@withContext
            }

            if (!forceUpdate && !appLogoProvider.isAppLogoExpired(theme)) {
                logger.debug("App logo not expired for theme={}", theme)
                return@withContext
            }

            logger.info("Downloading app logo from {}", logoUrl)
            val request = buildRequest {
                get()
                url(logoUrl)
            }
            okHttpClient.executeAsync(request).use { response ->
                when {
                    response.isSuccessful -> {
                        logger.debug("Logo found. Start download")

                        val temporaryFile = fileService.createTempFile(MediaStore.MEDIA_IGNORE_FILENAME, "appicon")
                        try {
                            val expires = response.getExpiration()
                            response.getSuccessBodyOrThrow()
                                .copyIntoFile(temporaryFile)
                            logger.info("App logo downloaded for theme={}", theme)

                            appLogoProvider.saveAppLogo(temporaryFile, theme, expires)
                        } finally {
                            temporaryFile.delete()
                        }
                    }

                    response.code == Http.StatusCode.NOT_FOUND -> logger.warn("Logo not found")
                    else -> logger.warn("Connection failed with response code {}", response.code)
                }
            }
        } catch (e: IOException) {
            logger.error("Update of app logo failed", e)
        }
    }
}
