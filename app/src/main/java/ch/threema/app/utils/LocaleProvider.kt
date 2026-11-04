package ch.threema.app.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import ch.threema.common.takeUnlessEmpty
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale

class LocaleProvider(
    private val appContext: Context,
) {
    fun getLanguage(): String =
        try {
            URLEncoder.encode(Locale.getDefault().language, Charsets.UTF_8.name())
        } catch (_: UnsupportedEncodingException) {
            "en"
        }

    fun getAppLanguage(): String =
        AppCompatDelegate.getApplicationLocales()
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?.language
            ?.takeUnlessEmpty()
            ?: getLanguage()

    fun getCurrentLocale(): Locale? =
        ConfigurationCompat.getLocales(appContext.resources.configuration).get(0)
}
