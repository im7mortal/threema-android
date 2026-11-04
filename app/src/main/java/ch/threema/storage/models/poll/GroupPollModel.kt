package ch.threema.storage.models.poll

import ch.threema.data.datatypes.LocalPollId

data class GroupPollModel(
    var id: Int = 0,
    var groupId: Int = 0,
    override var pollId: LocalPollId = 0,
) : LinkPollModel {

    override val type: LinkPollModel.Type
        get() = LinkPollModel.Type.GROUP

    companion object {
        const val TABLE = "group_ballot"
        const val COLUMN_ID = "id"
        const val COLUMN_GROUP_ID = "groupId"
        const val COLUMN_POLL_ID = "ballotId"

        @JvmStatic
        fun javaCreate(groupId: Int, pollId: LocalPollId) = GroupPollModel(
            groupId = groupId,
            pollId = pollId,
        )
    }
}
