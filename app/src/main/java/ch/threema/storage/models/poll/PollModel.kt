package ch.threema.storage.models.poll

import ch.threema.data.datatypes.ApiPollId
import ch.threema.data.datatypes.LocalPollId
import ch.threema.domain.types.IdentityString
import java.time.Instant

data class PollModel(
    var id: LocalPollId = 0,
    var apiPollId: ApiPollId,
    var creatorIdentity: IdentityString,
    var name: String? = null,
    var state: State,
    var assessment: Assessment,
    var type: Type,
    var choiceType: ChoiceType = ChoiceType.TEXT,
    var displayType: DisplayType? = null,
    var createdAt: Instant,
    var modifiedAt: Instant,
    var lastViewedAt: Instant? = null,
) {
    enum class State(@JvmField val serialized: String) {
        TEMPORARY("TEMPORARY"),
        OPEN("OPEN"),
        CLOSED("CLOSED"),
        ;

        companion object {
            @JvmStatic
            fun deserialize(value: String) =
                entries.find { it.serialized == value }
        }
    }

    enum class Assessment(@JvmField val serialized: String) {
        SINGLE_CHOICE("SINGLE_CHOICE"),
        MULTIPLE_CHOICE("MULTIPLE_CHOICE"),
        ;

        companion object {
            @JvmStatic
            fun deserialize(value: String) =
                entries.find { it.serialized == value }
        }
    }

    enum class Type(@JvmField val serialized: String) {
        RESULT_ON_CLOSE("RESULT_ON_CLOSE"),
        INTERMEDIATE("INTERMEDIATE"),
        ;

        companion object {
            @JvmStatic
            fun deserialize(value: String) =
                entries.find { it.serialized == value }
        }
    }

    enum class ChoiceType(@JvmField val serialized: String) {
        TEXT("TEXT"),
        ;

        companion object {
            @JvmStatic
            fun deserialize(value: String) =
                entries.find { it.serialized == value }
        }
    }

    enum class DisplayType(@JvmField val serialized: String) {
        LIST_MODE("LIST_MODE"),
        SUMMARY_MODE("SUMMARY_MODE"),
        ;

        companion object {
            @JvmStatic
            fun deserialize(value: String) =
                entries.find { it.serialized == value }
        }
    }

    companion object {
        const val TABLE = "ballot"
        const val COLUMN_ID = "id"
        const val COLUMN_API_POLL_ID = "apiBallotId"
        const val COLUMN_CREATOR_IDENTITY = "creatorIdentity"
        const val COLUMN_NAME = "name"
        const val COLUMN_STATE = "state"
        const val COLUMN_ASSESSMENT = "assessment"
        const val COLUMN_TYPE = "type"
        const val COLUMN_CHOICE_TYPE = "choiceType"
        const val COLUMN_DISPLAY_TYPE = "displayType"
        const val COLUMN_CREATED_AT = "createdAt"
        const val COLUMN_MODIFIED_AT = "modifiedAt"
        const val COLUMN_LAST_VIEWED_AT = "lastViewedAt"
    }
}
