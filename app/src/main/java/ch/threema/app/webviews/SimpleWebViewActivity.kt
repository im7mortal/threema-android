package ch.threema.app.webviews

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.view.InflateException
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.ui.InsetSides.Companion.lbr
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.webviews.usecases.MayOpenUriExternallyUseCase
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.consume
import ch.threema.common.truncate
import ch.threema.logging.logAndReportError
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SimpleWebViewActivity")

abstract class SimpleWebViewActivity : ThreemaToolbarActivity(), DialogClickListener {
    private val mayOpenUriExternallyUseCase: MayOpenUriExternallyUseCase by inject()

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var webView: WebView

    public override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
        } catch (e: InflateException) {
            // InflateException is primarily thrown here when no web view is installed on the device
            logger.error("Failed to show web view", e)
            val url = getWebViewUrl().toUri()
            if (url.scheme == "https") {
                openUrlExternally(url)
            } else {
                showToast(R.string.an_error_occurred)
            }
            finish()
            return
        }
        // TODO(ANDR-4389): Improve this entrypoint
        if (isFinishing) {
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.material_toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setTitle(getWebViewTitle())

        progressBar = findViewById(R.id.progress)
        webView = findViewById(R.id.simple_webview)
        webView.getSettings().javaScriptEnabled = requiresJavaScript()
        webView.setWebViewClient(ThreemaWebViewClient(::onNavigationRequest))

        if (requiresConnection()) {
            webView.setWebChromeClient(object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 99) {
                        progressBar.isInvisible = true
                    } else {
                        progressBar.progress = newProgress
                    }
                }
            })
            checkConnection()
        } else {
            progressBar.isVisible = false
            loadWebView()
        }
    }

    override fun handleDeviceInsets() {
        super.handleDeviceInsets()
        findViewById<View?>(R.id.webview_scroller).applyDeviceInsetsAsPadding(lbr())
    }

    private fun onNavigationRequest(uri: Uri): Boolean = consume {
        if (mayOpenUriExternallyUseCase.call(uri)) {
            // We don't want to allow navigating to other pages within the web view,
            // so instead we open the URL in the external browser
            openUrlExternally(uri)
        } else {
            logger.logAndReportError("Not allowed to open URL (scheme={}, host={})", uri.scheme, uri.host?.truncate(maxLength = 5))
            showToast(R.string.an_error_occurred)
        }
    }

    private fun openUrlExternally(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: ActivityNotFoundException) {
            logger.error("No activity found to open URL (scheme={}, host={})", url.scheme, url.host?.truncate(maxLength = 5))
            showToast(R.string.no_activity_for_intent)
        }
    }

    private fun loadWebView() {
        webView.loadUrl(getWebViewUrl())
    }

    private fun getWebViewUrl() =
        getWebViewUrl(ConfigUtils.isTheDarkSide(this))

    private fun checkConnection() {
        if (getSystemService<ConnectivityManager>()?.activeNetworkInfo?.isConnected == true) {
            loadWebView()
        } else {
            GenericAlertDialog.newInstance(
                getWebViewTitle(),
                R.string.internet_connection_required,
                R.string.retry,
                R.string.cancel,
            )
                .show(supportFragmentManager, DIALOG_TAG_NO_CONNECTION)
        }
    }

    override fun getLayoutResource() = R.layout.activity_simple_webview

    override fun onYes(tag: String?, data: Any?) {
        checkConnection()
    }

    override fun onNo(tag: String?, data: Any?) {
        finish()
    }

    @StringRes
    protected abstract fun getWebViewTitle(): Int

    protected abstract fun getWebViewUrl(isDarkTheme: Boolean): String

    protected open fun requiresConnection(): Boolean = true

    protected open fun requiresJavaScript(): Boolean = false

    companion object {
        private const val DIALOG_TAG_NO_CONNECTION = "nc"
    }
}
