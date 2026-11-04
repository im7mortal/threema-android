package ch.threema.app.webviews

import android.content.Context
import android.os.Bundle
import androidx.core.view.isInvisible
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import kotlin.getValue
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("PrivacyPolicyActivity")

class PrivacyPolicyActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val webViewUrlProvider: WebViewUrlProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.extras?.getBoolean(HIDE_CONNECTION_INDICATOR, false) == true) {
            connectionIndicator?.isInvisible = true
        }
    }

    override fun getWebViewTitle() = R.string.privacy_policy

    override fun getWebViewUrl(isDarkTheme: Boolean) =
        webViewUrlProvider.getPrivacyPolicyURL(isDarkTheme)

    companion object {
        private const val HIDE_CONNECTION_INDICATOR = "hideConnectionIndicator"

        @JvmStatic
        fun createIntent(context: Context, hideConnectionIndicator: Boolean = false) = buildActivityIntent<PrivacyPolicyActivity>(context) {
            putExtra(HIDE_CONNECTION_INDICATOR, hideConnectionIndicator)
        }
    }
}
