package ch.threema.storage.models.poll

import ch.threema.data.datatypes.LocalPollId

interface LinkPollModel {
    enum class Type(val serialized: String) {
        CONTACT("CONTACT"),
        GROUP("GROUP"),
    }
    val pollId: LocalPollId
    val type: Type
}
