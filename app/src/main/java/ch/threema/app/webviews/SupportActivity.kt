package ch.threema.app.webviews

import android.content.Context
import androidx.core.net.toUri
import ch.threema.android.buildActivityIntent
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.LocaleProvider
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SupportActivity")

class SupportActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val userService: UserService by inject()
    private val localeProvider: LocaleProvider by inject()

    override fun requiresConnection() = true

    override fun requiresJavaScript() = true

    override fun getWebViewTitle() = R.string.support

    override fun getWebViewUrl(isDarkTheme: Boolean) =
        getBaseUrl().toUri()
            .buildUpon()
            .appendQueryParameter("lang", localeProvider.getAppLanguage())
            .appendQueryParameter("version", ConfigUtils.getDeviceInfo(true))
            .appendQueryParameter("identity", userService.identity)
            .build()
            .toString()

    private fun getBaseUrl(): String {
        if (BuildFlavor.IS_WORK) {
            val customSupportUrl = preferenceService.getCustomSupportUrl()
            if (customSupportUrl != null) {
                return customSupportUrl
            }
        }
        return getString(R.string.support_url)
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<SupportActivity>(context)
    }
}
