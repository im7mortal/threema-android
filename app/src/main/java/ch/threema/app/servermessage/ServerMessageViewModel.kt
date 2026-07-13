package ch.threema.app.servermessage

import androidx.lifecycle.ViewModel
import ch.threema.app.services.notification.NotificationService
import ch.threema.data.repositories.ServerMessageModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServerMessageViewModel(
    private val serverMessageModelRepository: ServerMessageModelRepository,
    private val notificationService: NotificationService,
) : ViewModel() {
    private val _serverMessage = MutableStateFlow(popNextMessage())
    val serverMessage: StateFlow<String?> = _serverMessage.asStateFlow()

    fun markServerMessageAsRead() {
        // Delete currently shown message from database if the same message arrived again in the meantime.
        serverMessage.value?.let { previousMessage ->
            serverMessageModelRepository.deleteServerMessageByMessage(previousMessage)
        }

        val nextMessage = popNextMessage()

        // Cancel the server message notification as the "Another connection..." message
        // may be received several times. This would open another notification. Because the
        // message is the same, it is shown only once and therefore has been deleted at this
        // point.
        if (nextMessage == null) {
            notificationService.cancelServerMessageNotification()
        }

        // Post the next message. If it is null, then no server message is available
        _serverMessage.value = nextMessage
    }

    private fun popNextMessage(): String? =
        serverMessageModelRepository.popServerMessage()
            ?.message
}
