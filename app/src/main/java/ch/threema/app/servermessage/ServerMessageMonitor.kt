package ch.threema.app.servermessage

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.monitors.Monitor
import ch.threema.app.services.notification.NotificationService
import ch.threema.data.repositories.ServerMessageModelRepository
import org.koin.core.component.KoinComponent

class ServerMessageMonitor(
    private val serverMessageModelRepository: ServerMessageModelRepository,
) : Monitor("ServerMessageMonitor"), KoinComponent {
    private val notificationService: NotificationService? by injectNullableNonBinding()

    override suspend fun run() {
        serverMessageModelRepository.watchNewServerMessages().collect {
            notificationService?.showServerMessageNotification()
        }
    }
}
