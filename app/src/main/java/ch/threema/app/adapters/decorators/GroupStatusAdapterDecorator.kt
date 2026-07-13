package ch.threema.app.adapters.decorators

import android.content.Context
import ch.threema.app.R
import ch.threema.app.ui.listitemholder.ComposeMessageHolder
import ch.threema.app.utils.LinkifyUtil
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.data.status.GroupStatusDataModel
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.CREATED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.FIRST_VOTE
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.GROUP_DESCRIPTION_CHANGED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.IS_NOTES_GROUP
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.IS_PEOPLE_GROUP
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.MEMBER_ADDED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.MEMBER_KICKED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.MEMBER_LEFT
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.MODIFIED_VOTE
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.ORPHANED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.PROFILE_PICTURE_UPDATED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.RECEIVED_VOTE
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.RENAMED
import ch.threema.storage.models.data.status.GroupStatusDataModel.GroupStatusType.VOTES_COMPLETE
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GroupStatusAdapterDecorator(
    messageModel: AbstractMessageModel,
    chatAdapterDecoratorListener: ChatAdapterDecoratorListener,
    linkifyListener: LinkifyUtil.LinkifyListener,
    helper: Helper?,
) : ChatAdapterDecorator(messageModel, chatAdapterDecoratorListener, linkifyListener, helper), KoinComponent {
    private val identityProvider: IdentityProvider by inject()
    private val contactModelRepository: ContactModelRepository by inject()

    override fun configureChatMessage(holder: ComposeMessageHolder, context: Context, position: Int) {
        val statusDataModel = messageModel.groupStatusData ?: return
        val statusText = getStatusText(
            statusDataModel = statusDataModel,
            identityProvider = identityProvider,
            contactModelRepository = contactModelRepository,
            contactNameFormat = helper.preferenceService.getContactNameFormat(),
            context = context,
        )
        if (showHide(holder.bodyTextView, statusText.isNotEmpty())) {
            holder.bodyTextView.text = statusText
        }
        setOnClickListener(
            {
                // no action on onClick
            },
            holder.messageBlockView,
        )
    }

    companion object {
        @JvmStatic
        fun getStatusText(
            statusDataModel: GroupStatusDataModel,
            identityProvider: IdentityProvider,
            contactModelRepository: ContactModelRepository?,
            contactNameFormat: ContactNameFormat,
            context: Context,
        ): String {
            val myIdentity = identityProvider.getIdentityString()
            if (statusDataModel.identity == myIdentity) {
                when (statusDataModel.statusType) {
                    MEMBER_ADDED -> return context.getString(R.string.status_group_you_were_added)
                    MEMBER_LEFT -> return context.getString(R.string.status_group_you_left)
                    MEMBER_KICKED -> return context.getString(R.string.status_group_you_were_removed)
                    else -> Unit
                }
            }
            val displayName = getDisplayName(context, statusDataModel, myIdentity, contactModelRepository, contactNameFormat)
            val pollName = statusDataModel.pollName ?: ""
            val newGroupName = statusDataModel.newGroupName ?: ""
            return when (statusDataModel.statusType) {
                CREATED -> context.getString(R.string.status_create_group)
                RENAMED -> context.getString(R.string.status_rename_group, newGroupName)
                PROFILE_PICTURE_UPDATED -> context.getString(R.string.status_group_new_photo)
                MEMBER_ADDED -> context.getString(R.string.status_group_new_member, displayName)
                MEMBER_LEFT -> context.getString(R.string.status_group_member_left, displayName)
                MEMBER_KICKED -> context.getString(R.string.status_group_member_kicked, displayName)
                IS_NOTES_GROUP -> context.getString(R.string.status_create_notes)
                IS_PEOPLE_GROUP -> context.getString(R.string.status_create_notes_off)
                FIRST_VOTE -> context.getString(
                    R.string.status_ballot_user_first_vote,
                    displayName,
                    pollName,
                )
                MODIFIED_VOTE -> context.getString(
                    R.string.status_ballot_user_modified_vote,
                    displayName,
                    pollName,
                )
                RECEIVED_VOTE -> context.getString(
                    R.string.status_ballot_voting_changed,
                    pollName,
                )
                VOTES_COMPLETE -> context.getString(R.string.status_ballot_all_votes, pollName)
                GROUP_DESCRIPTION_CHANGED -> "" // TODO(ANDR-2386)
                ORPHANED -> context.getString(R.string.status_orphaned_group)
            }
        }

        /**
         * Get the display name of the identity contained in the group status data model.
         */
        private fun getDisplayName(
            context: Context,
            statusDataModel: GroupStatusDataModel,
            myIdentity: IdentityString?,
            contactModelRepository: ContactModelRepository?,
            contactNameFormat: ContactNameFormat,
        ): String {
            val identity = statusDataModel.identity ?: return ""
            if (identity == myIdentity) {
                return context.getString(R.string.me_myself_and_i)
            }
            return contactModelRepository?.getByIdentity(identity)
                ?.data
                ?.getDisplayName(contactNameFormat)
                ?: identity
        }
    }
}
