package ch.threema.app.identitylinks

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ch.threema.android.ResourceIdString
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.startup.finishAndRestartLaterIfNotReady
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SMSVerificationLinkActivity")

/**
 * This activity is exported to allow it to be opened via a deep link (e.g. from an SMS or email). Exporting it is safe because verification codes
 * extracted from incoming links are only forwarded to the server if a phone number verification is currently in progress and the code matches the
 * expected format. This prevents any abuse from arbitrary external launches.
 *
 * Since this activity does not display any sensitive information, there is no need to prompt the user to unlock the app when it is launched.
 */
class SMSVerificationLinkActivity : AppCompatActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val verifyMobileNumberUseCase: VerifyMobileNumberUseCase by inject()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishAndRestartLaterIfNotReady()) {
            return
        }

        lifecycleScope.launch {
            verifyMobileNumber()
            finish()
        }
    }

    private suspend fun verifyMobileNumber() {
        val verificationResult = verifyMobileNumberUseCase.call(
            verificationCode = intent.data?.getQueryParameter("code"),
        )

        val message = when (verificationResult) {
            is VerifyMobileNumberUseCase.VerificationResult.Success -> ResourceIdString(resId = R.string.verify_success_text)
            is VerifyMobileNumberUseCase.VerificationResult.Failure -> verificationResult.errorMessage
        }

        showToast(message = message.get(this), duration = ToastDuration.LONG)
    }
}
