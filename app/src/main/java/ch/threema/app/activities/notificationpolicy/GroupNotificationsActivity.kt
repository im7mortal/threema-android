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
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.types.GroupDatabaseId
import java.time.Instant
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("GroupNotificationsActivity")

class GroupNotificationsActivity : NotificationsActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val ringtoneService: RingtoneService by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val timeProvider: TimeProvider by inject()

    private val groupDatabaseId: GroupDatabaseId by lazy {
        intent.getLongExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, GROUP_ID_NOT_PASSED)
    }

    private val groupModel: GroupModel? by lazy {
        groupModelRepository.getByGroupDatabaseId(groupDatabaseId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (groupDatabaseId == GROUP_ID_NOT_PASSED || groupModel == null) {
            finish()
            return
        }
        conversationId = GroupConversationId(groupDatabaseId)
        refreshSettings()
    }

    public override fun refreshSettings() {
        defaultRingtone = ringtoneService.defaultGroupRingtone
        selectedRingtone = ringtoneService.getGroupRingtone(conversationId)
        super.refreshSettings()
    }

    override fun setupButtons() {
        super.setupButtons()
        radioSilentExceptMentions.isVisible = true
    }

    override fun onContactNotificationTriggerPolicyOverrideChanged(notificationTriggerPolicyOverride: ContactNotificationTriggerPolicyOverride?) {
        // Nothing to do here.
    }

    override fun onGroupNotificationTriggerPolicyOverrideChanged(notificationTriggerPolicyOverride: GroupNotificationTriggerPolicyOverride?) {
        groupModel?.setNotificationTriggerPolicyOverrideFromLocal(notificationTriggerPolicyOverride)
    }

    override fun isMutedRightNow(): Boolean =
        groupModel?.data?.notificationTriggerPolicyOverride?.muteAppliesAt(timeProvider.get())
            ?: false

    override fun isMutedExceptMentions(): Boolean =
        groupModel?.data?.notificationTriggerPolicyOverride?.policy == GroupNotificationTriggerPolicyOverridePolicy.MENTIONED

    override fun getExpiresAt(): Instant? =
        groupModel?.data?.notificationTriggerPolicyOverride?.expiresAt

    companion object {
        @JvmStatic
        fun createIntent(
            context: Context,
            groupDatabaseId: GroupDatabaseId,
            conversationName: String?,
        ) = buildActivityIntent<GroupNotificationsActivity>(context) {
            putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, groupDatabaseId)
            putExtra(AppConstants.INTENT_DATA_TEXT, conversationName)
        }

        private const val GROUP_ID_NOT_PASSED = -1L
    }
}
