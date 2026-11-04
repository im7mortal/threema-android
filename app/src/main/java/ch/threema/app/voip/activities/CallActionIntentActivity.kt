package ch.threema.app.voip.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.lifecycle.lifecycleScope
import ch.threema.android.ToastDuration
import ch.threema.android.firstOrNull
import ch.threema.android.isContentUri
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.AppLockExempt
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.applock.CheckAppLockContract
import ch.threema.app.di.awaitAppFullyReady
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.license.LicenseService
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.voip.util.VoipUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("CallActionIntentActivity")

/**
 * Handle call action intents (sent by calling someone through the Android phonebook),
 * start the call activity.
 */
class CallActionIntentActivity : ThreemaActivity(), AppLockExempt {
    init {
        logScreenVisibility(logger)
    }

    private val appRestrictions: AppRestrictions by inject()
    private val contactModelRepository: ContactModelRepository by inject()
    private val licenseService: LicenseService<*> by inject()
    private val synchronizedSettingsService: SynchronizedSettingsService by inject()

    private val checkLockLauncher = registerForActivityResult(CheckAppLockContract(promptIfUnlocked = false)) { unlocked ->
        if (unlocked) {
            lifecycleScope.launch {
                awaitAppFullyReady()
                onUnlocked()
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            checkLockLauncher.launch(Unit)
        }
    }

    private fun onUnlocked() {
        if (!synchronizedSettingsService.isVoipEnabled() || appRestrictions.isCallsDisabled() || !licenseService.isLicensed) {
            showToast(R.string.voip_disabled, ToastDuration.LONG)
            finish()
            return
        }
        if (intent.action != Intent.ACTION_VIEW) {
            logger.error("Unexpected intent action {}", intent.action)
            finish()
            return
        }
        if (intent.type != getString(R.string.call_mime_type)) {
            logger.error("Unexpected intent type {}", intent.type)
            finish()
            return
        }

        val contactModel = intent.data
            ?.takeIf { it.isContentUri() }
            ?.let(::getContactModelForContentUri)
        val contactModelData = contactModel?.data
        if (contactModelData == null) {
            showToast(R.string.voip_contact_not_found, ToastDuration.LONG)
            logger.warn("Invalid call intent: Contact not found")
            finish()
            return
        }

        logger.info("Calling {} via call intent action", contactModel.identity)
        val callInitiated = VoipUtil.initiateCall(
            /* activity = */
            this,
            /* contactIdentity = */
            contactModel.identity,
            /* contactFeatureMask = */
            contactModelData.featureMaskLong(),
            /* launchVideo = */
            false,
            /* onFinishRunnable = */
            { finish() },
        )
        if (!callInitiated) {
            finish()
        }
    }

    private fun getContactModelForContentUri(uri: Uri): ContactModel? =
        try {
            contentResolver.query(uri, null, null, null, null)
                ?.firstOrNull { cursor ->
                    val contactIdentity = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.Data.DATA1))
                    contactModelRepository.getByIdentity(contactIdentity)
                }
        } catch (e: SecurityException) {
            logger.error("Failed to get contact", e)
            null
        }
}
