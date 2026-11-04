package ch.threema.app.adapters.decorators

import androidx.media3.session.MediaController
import ch.threema.app.services.messageplayer.MessagePlayer
import ch.threema.storage.models.MessageModel
import com.google.common.util.concurrent.ListenableFuture

interface MessagePlayerFactory {
    fun create(
        messageModel: MessageModel,
        mediaControllerFuture: ListenableFuture<MediaController?>?,
    ): MessagePlayer
}
