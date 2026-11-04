package ch.threema.app.libthreema.conversion

import ch.threema.libthreema.TextMessageState
import ch.threema.storage.models.MessageType

class TextMessageStateConverter {
    fun convertToMessageBodyAndRevisions(textMessageState: TextMessageState): MessageBodyAndRevisions {
        val messageEdits = textMessageState
            .revisions
            .map { revision ->
                MessageEdit(
                    createdAt = revision.createdAt,
                    body = revision.message.text,
                )
            }

        return MessageBodyAndRevisions(
            messageType = MessageType.TEXT,
            originalBody = textMessageState.original.text,
            messageEdits = messageEdits,
        )
    }
}
