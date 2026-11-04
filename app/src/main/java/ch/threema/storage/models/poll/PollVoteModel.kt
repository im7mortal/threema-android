package ch.threema.storage.models.poll

import ch.threema.data.datatypes.LocalPollChoiceId
import ch.threema.data.datatypes.LocalPollId
import java.time.Instant

data class PollVoteModel(
    var id: Int = 0,
    var pollId: LocalPollId = 0,
    var pollChoiceId: LocalPollChoiceId = 0,
    var votingIdentity: String? = null,
    var selected: Boolean = false,
    var createdAt: Instant? = null,
    var modifiedAt: Instant? = null,
) {
    var choice: Int
        get() = if (selected) 1 else 0
        set(value) {
            selected = value > 0
        }

    companion object {
        const val TABLE = "ballot_vote"
        const val COLUMN_ID = "id"
        const val COLUMN_POLL_ID = "ballotId"
        const val COLUMN_POLL_CHOICE_ID = "ballotChoiceId"
        const val COLUMN_VOTING_IDENTITY = "votingIdentity"
        const val COLUMN_CHOICE = "choice"
        const val COLUMN_CREATED_AT = "createdAt"
        const val COLUMN_MODIFIED_AT = "modifiedAt"
    }
}
