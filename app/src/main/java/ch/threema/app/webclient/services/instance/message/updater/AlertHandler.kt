package ch.threema.app.webclient.services.instance.message.updater

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.app.webclient.Protocol
import ch.threema.app.webclient.converter.MsgpackObjectBuilder
import ch.threema.app.webclient.services.instance.MessageDispatcher
import ch.threema.app.webclient.services.instance.MessageUpdater
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.repositories.ServerMessageModelRepository
import ch.threema.storage.models.ServerMessageModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.msgpack.core.MessagePackException

private val logger = getThreemaLogger("AlertHandler")

@WorkerThread
class AlertHandler @AnyThread constructor(
    private val handler: HandlerExecutor,
    private val updateDispatcher: MessageDispatcher,
) : MessageUpdater(Protocol.SUB_TYPE_ALERT), KoinComponent {

    private val serverMessageModelRepository: ServerMessageModelRepository by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private var coroutineScope: CoroutineScope? = null

    override fun register() {
        coroutineScope?.cancel()
        coroutineScope = CoroutineScope(dispatcherProvider.worker)

        coroutineScope?.launch {
            serverMessageModelRepository.watchNewServerMessages().collect { serverMessage ->
                val type = when (serverMessage.type) {
                    ServerMessageModel.TYPE_ALERT -> ALERT_TYPE_WARNING
                    ServerMessageModel.TYPE_ERROR -> ALERT_TYPE_ERROR
                    else -> {
                        logger.error("Invalid server message type: {}", serverMessage.type)
                        return@collect
                    }
                }
                handler.post {
                    sendAlertMessage(type, serverMessage.message)
                }
            }
        }
    }

    override fun unregister() {
        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun sendAlertMessage(type: String?, message: String?) {
        try {
            send(
                updateDispatcher,
                MsgpackObjectBuilder()
                    .put(Protocol.ARGUMENT_ALERT_MESSAGE, message),
                MsgpackObjectBuilder()
                    .put(Protocol.ARGUMENT_ALERT_TYPE, type)
                    .put(Protocol.ARGUMENT_ALERT_SOURCE, SOURCE_SERVER),
            )
        } catch (e: MessagePackException) {
            logger.error("Failed to send alert", e)
        }
    }

    companion object {
        const val SOURCE_SERVER = "server"
        const val ALERT_TYPE_ERROR = "error"
        const val ALERT_TYPE_WARNING = "warning"
    }
}
