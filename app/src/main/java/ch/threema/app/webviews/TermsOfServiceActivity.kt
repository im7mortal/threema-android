package ch.threema.app.webviews

import android.content.Context
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import kotlin.getValue
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("TermsOfServiceActivity")

class TermsOfServiceActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val webViewUrlProvider: WebViewUrlProvider by inject()

    override fun getWebViewTitle() = R.string.terms_of_service

    override fun getWebViewUrl(isDarkTheme: Boolean) =
        webViewUrlProvider.getTermsOfServiceURL(isDarkTheme)

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<TermsOfServiceActivity>(context)
    }
}
