package ch.threema.app.webviews

import android.content.Context
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ReferralTosActivity")

class ReferralTosActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val webViewUrlProvider: WebViewUrlProvider by inject()

    override fun getWebViewTitle() = R.string.terms_of_service

    override fun getWebViewUrl(isDarkTheme: Boolean) =
        webViewUrlProvider.getReferralTermsOfServiceURL(isDarkTheme)

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<ReferralTosActivity>(context)
    }
}
