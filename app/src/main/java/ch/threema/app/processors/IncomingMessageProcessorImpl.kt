package ch.threema.app.processors

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.managers.ServiceManager
import ch.threema.app.typingindicator.TypingIndicatorManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.repositories.ServerMessageModelRepository
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.storage.models.ServerMessageModel

private val logger = getThreemaLogger("IncomingMessageProcessorImpl")

class IncomingMessageProcessorImpl(
    private val serviceManager: ServiceManager,
    private val serverMessageModelRepository: ServerMessageModelRepository,
    private val globalEventBuses: GlobalEventBuses,
    private val typingIndicatorManager: TypingIndicatorManager,
) : IncomingMessageProcessor {

    override suspend fun processIncomingCspMessage(
        messageBox: MessageBox,
        handle: ActiveTaskCodec,
    ) {
        IncomingMessageTask(messageBox, serviceManager, globalEventBuses, typingIndicatorManager).run(handle)
    }

    override suspend fun processIncomingD2mMessage(
        message: InboundD2mMessage.Reflected,
        handle: ActiveTaskCodec,
    ) {
        IncomingReflectedMessageTask(message, serviceManager, globalEventBuses, typingIndicatorManager).run(handle)
    }

    override fun processIncomingServerAlert(alertData: CspMessage.ServerAlertData) {
        val message = ServerMessageModel(alertData.message, ServerMessageModel.TYPE_ALERT)
        serverMessageModelRepository.saveServerMessage(message)
    }

    override fun processIncomingServerError(errorData: CspMessage.ServerErrorData) {
        val errorMessage = errorData.message
        if (errorMessage.contains("Another connection")) {
            // See `MonitoringLayer#handleCloseError(CspContainer)` for more info
            logger.info("Do not display `Another connection` close-error")
            return
        }
        val message = ServerMessageModel(errorMessage, ServerMessageModel.TYPE_ERROR)
        serverMessageModelRepository.saveServerMessage(message)
    }
}
