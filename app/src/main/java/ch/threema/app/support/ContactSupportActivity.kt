package ch.threema.app.support

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import ch.threema.android.buildActivityIntent
import ch.threema.android.buildIntent
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.dialogs.GenericProgressDialog
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ContactSupportActivity : ThreemaActivity() {
    private val viewModel: ContactSupportViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildFlavor.IS_ON_PREM) {
            finish()
            return
        }
        lifecycleScope.launch {
            viewModel.events.collect(::handleEvent)
        }
        GenericProgressDialog.newInstance(
            null,
            getString(R.string.please_wait),
        )
            .show(
                /* manager = */
                supportFragmentManager,
                /* tag = */
                DIALOG_PROGRESS,
            )
    }

    private fun handleEvent(event: ContactSupportViewEvent) {
        when (event) {
            is ContactSupportViewEvent.OnSupportContactAvailable -> {
                val intent = ComposeMessageActivity.createIntent(this, event.conversationId)
                startActivity(intent)
                finish()
            }
            is ContactSupportViewEvent.OnSupportContactUnavailable -> {
                val resultIntent = buildIntent {
                    putExtra(EXTRA_ERROR_MESSAGE, getString(event.message))
                }
                setResult(RESULT_ERROR, resultIntent)
                finish()
            }
        }
    }

    class Contract : ActivityResultContract<Unit, String?>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            createIntent(context)

        override fun parseResult(resultCode: Int, intent: Intent?): String? =
            intent?.getStringExtra(EXTRA_ERROR_MESSAGE)
    }

    companion object {
        private const val RESULT_ERROR = 2
        private const val EXTRA_ERROR_MESSAGE = "error_message"
        private const val DIALOG_PROGRESS = "prg"

        private fun createIntent(context: Context) = buildActivityIntent<ContactSupportActivity>(context)
    }
}
