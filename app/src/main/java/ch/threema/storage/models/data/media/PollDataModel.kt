package ch.threema.storage.models.data.media

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.JsonArrayIterator
import ch.threema.storage.models.data.MessageDataInterface
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

data class PollDataModel(
    val type: Type,
    val pollId: Int,
) : MessageDataInterface {
    override fun toString(): String =
        buildJsonArray {
            add(type.id)
            add(pollId)
        }
            .toString()

    enum class Type(val id: Int) {
        POLL_CREATED(1),
        POLL_MODIFIED(2),
        POLL_CLOSED(3),
    }

    companion object {
        private val logger = getThreemaLogger("PollDataModel")

        @JvmStatic
        fun deserialize(jsonString: String): PollDataModel =
            try {
                val iterator = JsonArrayIterator(jsonString)
                PollDataModel(
                    type = when (val typeId = iterator.nextInt()) {
                        Type.POLL_CREATED.id -> Type.POLL_CREATED
                        Type.POLL_MODIFIED.id -> Type.POLL_MODIFIED
                        Type.POLL_CLOSED.id -> Type.POLL_CLOSED
                        else -> error("Unexpected poll type id $typeId")
                    },
                    pollId = iterator.nextInt(),
                )
            } catch (e: Exception) {
                logger.error("Failed to parse poll data model", e)
                createEmpty()
            }

        /**
         * Do not use this in new code. It only exists to handle places where a [PollDataModel] needs to be returned and `null` is not allowed.
         */
        @Deprecated("Do not use this in new code")
        fun createEmpty(): PollDataModel =
            PollDataModel(
                type = Type.POLL_CLOSED,
                pollId = 0,
            )
    }
}
