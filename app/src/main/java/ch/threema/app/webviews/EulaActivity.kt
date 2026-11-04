package ch.threema.app.webviews

import android.content.Context
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import kotlin.getValue
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("EulaActivity")

class EulaActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val webViewUrlProvider: WebViewUrlProvider by inject()

    override fun getWebViewTitle() = R.string.eula

    override fun getWebViewUrl(isDarkTheme: Boolean) =
        webViewUrlProvider.getEulaURL(isDarkTheme)

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<EulaActivity>(context)
    }
}
