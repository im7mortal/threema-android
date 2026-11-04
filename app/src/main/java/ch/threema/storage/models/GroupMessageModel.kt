package ch.threema.storage.models

import ch.threema.domain.types.IdentityString

class GroupMessageModel : MessageModel() {
    /**
     * Returns the ID of the group model this message belongs to. This is different from the GroupID object!
     *
     * @return ID of group model
     */
    var groupId: Int = 0

    // TODO(ANDR-3325): This is only used for group ack/dec and can therefore be removed
    //  when the database is migrated.
    var groupMessageStates: MutableMap<IdentityString, String?>? = null

    override fun toString() = "GroupMessageModel(id=$id)"

    companion object {
        const val TABLE = "m_group_message"
        const val COLUMN_GROUP_ID = "groupId"
        const val COLUMN_GROUP_MESSAGE_STATES = "groupMessageStates"
    }
}
