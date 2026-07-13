package ch.threema.app.servermessage

import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.startup.finishAndRestartLaterIfNotReady
import ch.threema.app.ui.InsetSides.Companion.lbr
import ch.threema.app.ui.InsetSides.Companion.ltr
import ch.threema.app.ui.SpacingValues.Companion.all
import ch.threema.app.ui.applyDeviceInsetsAsMargin
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.consume
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

private val logger = getThreemaLogger("ServerMessageActivity")

class ServerMessageActivity : ThreemaActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val viewModel: ServerMessageViewModel by viewModel()

    private lateinit var serverMessageTextView: TextView

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishAndRestartLaterIfNotReady()) {
            return
        }

        supportActionBar?.let { actionBar ->
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(R.string.warning)
        }

        setContentView(R.layout.activity_server_message)

        serverMessageTextView = findViewById(R.id.server_message_text)
        serverMessageTextView.movementMethod = LinkMovementMethod.getInstance()

        handleDeviceInsets()

        findViewById<View>(R.id.close_button).setOnClickListener {
            viewModel.markServerMessageAsRead()
        }

        lifecycleScope.launch {
            viewModel.serverMessage.collect { serverMessage ->
                if (serverMessage == null) {
                    finish()
                } else {
                    showServerMessage(normalizeMessage(serverMessage))
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.markServerMessageAsRead()
                }
            },
        )
    }

    private fun normalizeMessage(message: String): String {
        if (message.startsWith("Another connection")) {
            return getString(R.string.another_connection_instructions, getString(R.string.app_name))
        }
        return message
    }

    private fun handleDeviceInsets() {
        findViewById<View?>(R.id.scroll_container).applyDeviceInsetsAsPadding(ltr(), all(R.dimen.grid_unit_x2))
        findViewById<View?>(R.id.close_button).applyDeviceInsetsAsMargin(lbr(), all(R.dimen.grid_unit_x2))
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> consume {
                viewModel.markServerMessageAsRead()
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun showServerMessage(message: String) {
        serverMessageTextView.text = HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<ServerMessageActivity>(context)
    }
}
