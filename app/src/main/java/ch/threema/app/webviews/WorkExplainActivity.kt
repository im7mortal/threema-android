package ch.threema.app.webviews

import android.content.Context
import android.os.Bundle
import ch.threema.android.buildActivityIntent
import ch.threema.android.disableEnterTransition
import ch.threema.android.disableExitTransition
import ch.threema.android.isAppInstalled
import ch.threema.app.R
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import kotlin.getValue
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("WorkExplainActivity")

class WorkExplainActivity : SimpleWebViewActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val webViewUrlProvider: WebViewUrlProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (isAppInstalled(this, WORK_PACKAGE_NAME)) {
            val launchIntent = packageManager.getLaunchIntentForPackage(WORK_PACKAGE_NAME)
            if (launchIntent != null) {
                startActivity(launchIntent)
                disableEnterTransition()
            }
            finish()
            disableExitTransition()
        }
        super.onCreate(savedInstanceState)
    }

    override fun getWebViewTitle() = R.string.threema_work

    override fun getWebViewUrl(isDarkTheme: Boolean) =
        webViewUrlProvider.getWorkExplainURL()

    companion object {
        private const val WORK_PACKAGE_NAME = "ch.threema.app.work"

        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<WorkExplainActivity>(context)
    }
}
