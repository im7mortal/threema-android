package ch.threema.storage.models

import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.models.GroupModel
import ch.threema.storage.models.group.GroupModelOld
import java.time.Instant

class ConversationModel(
    @JvmField var messageReceiver: MessageReceiver<*>,
) {

    val id: ConversationId = when {
        isContactConversation -> ContactConversationId(identity = contact!!.identity)
        isGroupConversation -> GroupConversationId(groupDatabaseId = group!!.id.toLong())
        isDistributionListConversation -> DistributionListConversationId(distributionListId = distributionList!!.id)
        else -> error("Can not determine id of conversation model for receiver of type ${messageReceiver.type}")
    }

    val isContactConversation: Boolean
        get() = messageReceiver.type == MessageReceiver.Type_CONTACT

    val isGroupConversation: Boolean
        get() = messageReceiver.type == MessageReceiver.Type_GROUP

    val isDistributionListConversation: Boolean
        get() = messageReceiver.type == MessageReceiver.Type_DISTRIBUTION_LIST

    @Deprecated("Use new contact model instead")
    val contact: ContactModel?
        get() = when {
            isContactConversation -> (messageReceiver as ContactMessageReceiver).contact
            else -> null
        }

    val contactModel: ch.threema.data.models.ContactModel?
        get() = when {
            isContactConversation -> (messageReceiver as ContactMessageReceiver).contactModel
            else -> null
        }

    @Deprecated("Use new group model instead")
    val group: GroupModelOld?
        get() = when {
            isGroupConversation -> (messageReceiver as GroupMessageReceiver).group
            else -> null
        }

    val groupModel: GroupModel?
        get() = when {
            isGroupConversation -> (messageReceiver as GroupMessageReceiver).groupModel
            else -> null
        }

    val distributionList: DistributionListModel?
        get() = when {
            isDistributionListConversation -> (messageReceiver as DistributionListMessageReceiver).distributionList
            else -> null
        }

    @JvmField
    var messageCount: Long = 0L

    @JvmField
    var latestMessage: AbstractMessageModel? = null

    var unreadCount: Long = 0L
        set(value) {
            field = value
            if (value == 0L) {
                isUnreadTagged = false
            }
        }

    @JvmField
    var isUnreadTagged: Boolean = false

    @JvmField
    var conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL

    // Only used by the web-client
    var position: Int = -1

    @JvmField
    var lastUpdate: Instant? = null

    /**
     * @return Return the date used for sorting. Corresponds to [lastUpdate] if set.
     */
    val sortDate: Instant
        get() = lastUpdate ?: Instant.EPOCH

    fun hasUnreadMessage(): Boolean = unreadCount > 0

    val receiverModel: ReceiverModel
        get() = contact ?: group ?: distributionList ?: throw IllegalStateException("ConversationModel is missing a ReceiverModel")

    override fun toString(): String = id.toString()
}
