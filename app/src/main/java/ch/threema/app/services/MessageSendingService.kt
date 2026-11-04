package ch.threema.app.services

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.MessageModel

interface MessageSendingService {
    interface MessageSendingProcess {
        val receiver: MessageReceiver<MessageModel>

        val messageModel: MessageModel

        @Throws(Exception::class)
        fun send(): Boolean
    }

    fun addToQueue(process: MessageSendingProcess)

    fun abort(messageUid: MessageUid)
}
