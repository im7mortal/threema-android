package ch.threema.storage.models.poll

import ch.threema.data.datatypes.LocalPollId
import ch.threema.domain.types.IdentityString

data class IdentityPollModel(
    var id: Int = 0,
    var identity: IdentityString,
    override var pollId: LocalPollId = 0,
) : LinkPollModel {

    override val type: LinkPollModel.Type
        get() = LinkPollModel.Type.CONTACT

    companion object {
        const val TABLE = "identity_ballot"
        const val COLUMN_ID = "id"
        const val COLUMN_IDENTITY = "identity"
        const val COLUMN_POLL_ID = "ballotId"

        @JvmStatic
        fun javaCreate(identity: IdentityString, pollId: LocalPollId) = IdentityPollModel(
            identity = identity,
            pollId = pollId,
        )
    }
}
