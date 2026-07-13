package ch.threema.app.activities.notificationpolicy

import android.content.Context
import android.os.Bundle
import androidx.core.view.isVisible
import ch.threema.android.buildActivityIntent
import ch.threema.app.AppConstants
import ch.threema.app.services.RingtoneService
import ch.threema.app.ui.muteAppliesAt
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.IdentityString
import java.time.Instant
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ContactNotificationsActivity")

class ContactNotificationsActivity : NotificationsActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val ringtoneService: RingtoneService by inject()
    private val contactModelRepository: ContactModelRepository by inject()
    private val timeProvider: TimeProvider by inject()

    private val contactIdentity: IdentityString? by lazy {
        intent.getStringExtra(AppConstants.INTENT_DATA_CONTACT)
    }

    private val contactModel: ContactModel? by lazy {
        contactIdentity?.let(contactModelRepository::getByIdentity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (contactIdentity.isNullOrEmpty() || contactModel == null) {
            finish()
            return
        }
        conversationId = contactIdentity?.let(::ContactConversationId)
        refreshSettings()
    }

    public override fun refreshSettings() {
        defaultRingtone = ringtoneService.defaultContactRingtone
        selectedRingtone = ringtoneService.getContactRingtone(conversationId)
        super.refreshSettings()
    }

    override fun setupButtons() {
        super.setupButtons()

        radioSilentExceptMentions.isVisible = false
    }

    override fun onContactNotificationTriggerPolicyOverrideChanged(notificationTriggerPolicyOverride: ContactNotificationTriggerPolicyOverride?) {
        contactModel?.setNotificationTriggerPolicyOverrideFromLocal(notificationTriggerPolicyOverride)
    }

    override fun onGroupNotificationTriggerPolicyOverrideChanged(notificationTriggerPolicyOverride: GroupNotificationTriggerPolicyOverride?) {
        // Nothing to do here.
    }

    override fun isMutedRightNow(): Boolean =
        contactModel?.data?.notificationTriggerPolicyOverride?.muteAppliesAt(timeProvider.get()) ?: false

    // This setting is only available for group chats
    override fun isMutedExceptMentions(): Boolean =
        false

    override fun getExpiresAt(): Instant? =
        contactModel?.data?.notificationTriggerPolicyOverride?.expiresAt

    companion object {
        @JvmStatic
        fun createIntent(
            context: Context,
            identity: IdentityString,
            conversationName: String?,
        ) = buildActivityIntent<ContactNotificationsActivity>(context) {
            putExtra(AppConstants.INTENT_DATA_CONTACT, identity)
            putExtra(AppConstants.INTENT_DATA_TEXT, conversationName)
        }
    }
}
