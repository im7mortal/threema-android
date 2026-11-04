package ch.threema.app.webviews

import android.content.Context
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.utils.LocaleProvider

class WebViewUrlProvider(
    private val appContext: Context,
    private val localeProvider: LocaleProvider,
) {
    fun getEulaURL(isDarkTheme: Boolean): String =
        appContext.getString(
            R.string.eula_url,
            getLanguageUrlParamValue(),
            BuildConfig.VERSION_NAME,
            getThemeUrlParamValue(isDarkTheme),
        )

    fun getPrivacyPolicyURL(isDarkTheme: Boolean): String =
        appContext.getString(
            R.string.privacy_policy_url,
            getLanguageUrlParamValue(),
            BuildConfig.VERSION_NAME,
            getThemeUrlParamValue(isDarkTheme),
        )

    fun getReferralTermsOfServiceURL(isDarkTheme: Boolean): String =
        appContext.getString(
            R.string.referral_terms_of_service_url,
            getLanguageUrlParamValue(),
            getThemeUrlParamValue(isDarkTheme),
        )

    fun getTermsOfServiceURL(isDarkTheme: Boolean): String =
        appContext.getString(
            R.string.terms_of_service_url,
            getLanguageUrlParamValue(),
            BuildConfig.VERSION_NAME,
            getThemeUrlParamValue(isDarkTheme),
        )

    fun getWorkExplainURL(): String {
        val language = localeProvider.getAppLanguage()
            .let { language ->
                if (language.startsWith("gsw")) "de" else language
            }
            .takeIf { it.length >= 2 }
            ?.take(2)
            ?: "en"
        return appContext.getString(R.string.work_explain_url, language)
    }

    private fun getLanguageUrlParamValue(): String {
        val language = localeProvider.getAppLanguage()
        return if (language.startsWith("de") || language.startsWith("gsw")) {
            "de"
        } else {
            "en"
        }
    }

    private fun getThemeUrlParamValue(isDarkTheme: Boolean): String {
        return if (isDarkTheme) LICENSE_URL_THEME_VALUE_DARK else LICENSE_URL_THEME_VALUE_LIGHT
    }

    companion object {
        private const val LICENSE_URL_THEME_VALUE_LIGHT = "light"
        private const val LICENSE_URL_THEME_VALUE_DARK = "dark"
    }
}
