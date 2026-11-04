package ch.threema.storage.models.poll

import ch.threema.data.datatypes.ApiPollChoiceId
import ch.threema.data.datatypes.LocalPollChoiceId
import ch.threema.data.datatypes.LocalPollId
import java.time.Instant

data class PollChoiceModel(
    var id: LocalPollChoiceId = 0,
    var pollId: LocalPollId = 0,
    var apiPollChoiceId: ApiPollChoiceId = 0,
    var name: String? = null,
    var voteCount: Int = 0,
    var order: Int = 0,
    var createdAt: Instant? = null,
    var modifiedAt: Instant? = null,
) {
    val type = Type.Text

    enum class Type(@JvmField val serialized: String) {
        Text("Text"),
    }

    companion object {
        const val TABLE = "ballot_choice"
        const val COLUMN_ID = "id"
        const val COLUMN_POLL_ID = "ballotId"
        const val COLUMN_API_CHOICE_ID = "apiBallotChoiceId"
        const val COLUMN_TYPE = "type"
        const val COLUMN_NAME = "name"
        const val COLUMN_VOTE_COUNT = "voteCount"
        const val COLUMN_ORDER = "order"
        const val COLUMN_CREATED_AT = "createdAt"
        const val COLUMN_MODIFIED_AT = "modifiedAt"
    }
}
