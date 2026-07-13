package ch.threema.app.usecases

import android.content.Context
import android.content.Intent
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.MimeUtil
import ch.threema.data.IdentityProvider

class GetInviteFriendIntentUseCase(
    private val appContext: Context,
    private val identityProvider: IdentityProvider,
) {

    fun call(targetPackageName: String): Intent {
        val isShortMessage = shouldUseShortMessage(targetPackageName)

        val intent = Intent(Intent.ACTION_SEND)
            .setType(MimeUtil.MIME_TYPE_TEXT)
            .setPackage(targetPackageName)

        val myIdentity = identityProvider.getIdentityString()!!

        if (isShortMessage) {
            val messageBody = appContext.getString(R.string.invite_sms_body, appContext.getString(R.string.app_name), myIdentity)
            intent.putExtra(Intent.EXTRA_TEXT, messageBody)
        } else {
            val messageBody = appContext.getString(R.string.invite_email_body, appContext.getString(R.string.app_name), myIdentity)
            intent.putExtra(Intent.EXTRA_SUBJECT, appContext.getString(R.string.invite_email_subject))
            intent.putExtra(Intent.EXTRA_TEXT, messageBody)
        }

        return intent
    }

    private fun shouldUseShortMessage(packageName: String) =
        if (packageName.contains("twitter")) {
            true
        } else {
            ConfigUtils.checkManifestPermission(appContext, packageName, "android.permission.SEND_SMS")
        }
}
