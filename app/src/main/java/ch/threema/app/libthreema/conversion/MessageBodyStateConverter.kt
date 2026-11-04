package ch.threema.app.libthreema.conversion

import ch.threema.common.toEpochMilliInstant
import ch.threema.data.datatypes.ApiPollId
import ch.threema.data.datatypes.LocalPollId
import ch.threema.libthreema.MessageStateBody as LibthreemaMessageStateBody
import ch.threema.storage.factories.PollModelFactory
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.PollDataModel
import java.time.Instant

data class MessageBodyAndRevisions(
    val messageType: MessageType,
    val originalBody: String?,
    /**
     * The message edits cannot be mapped directly to our database. They contain the edited body with the corresponding timestamp.
     */
    val messageEdits: List<MessageEdit> = emptyList(),
    val deletedAt: Instant? = null,
)

class MessageBodyStateConverter(
    private val textMessageStateConverter: TextMessageStateConverter,
    private val locationMessageStateConverter: LocationMessageStateConverter,
    private val deletedMessageStateConverter: DeletedMessageStateConverter,
    private val pollModelFactory: PollModelFactory,
) {
    fun convertToMessageBodyAndRevisions(messageStateBody: LibthreemaMessageStateBody): MessageBodyAndRevisions =
        when (messageStateBody) {
            is LibthreemaMessageStateBody.Text -> textMessageStateConverter.convertToMessageBodyAndRevisions(messageStateBody.v1)
            is LibthreemaMessageStateBody.Location -> locationMessageStateConverter.convertToMessageBodyAndRevisions(messageStateBody.v1)
            is LibthreemaMessageStateBody.PollClose -> {
                val pollId = getPollId(messageStateBody.v1.id.toHexString())
                MessageBodyAndRevisions(
                    messageType = MessageType.POLL,
                    originalBody = PollDataModel.createClosed(pollId).serialize(),
                )
            }

            is LibthreemaMessageStateBody.PollOpen -> {
                val pollId = getPollId(messageStateBody.v1.id.toHexString())
                MessageBodyAndRevisions(
                    messageType = MessageType.POLL,
                    originalBody = PollDataModel.createCreated(pollId).serialize(),
                )
            }

            is LibthreemaMessageStateBody.Deleted -> deletedMessageStateConverter.convertToMessageBodyAndRevisions(
                messageStateBodyType = messageStateBody.v1.previousMessageType,
                messageStateBody.v1.deletedAt.toEpochMilliInstant(),
            )
        }

    private fun getPollId(apiPollId: ApiPollId): LocalPollId =
        pollModelFactory.getLocalPollIdByApiPollId(apiPollId)
            ?: -1 // if the poll is unknown, we fall back to a dummy value. This is not ideal, but the UI can handle it
}
