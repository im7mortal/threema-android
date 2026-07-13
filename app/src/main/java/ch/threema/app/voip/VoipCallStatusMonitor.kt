package ch.threema.app.voip

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.VoipCallEvent
import ch.threema.app.monitors.Monitor
import ch.threema.app.services.ContactService
import ch.threema.app.services.MessageService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.IdentityProvider
import ch.threema.domain.types.Identity
import ch.threema.storage.models.data.status.VoipStatusDataModel
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("VoipCallStatusMonitor")

class VoipCallStatusMonitor(
    private val globalEventFlows: GlobalEventFlows,
    private val identityProvider: IdentityProvider,
) : Monitor("VoipCallStatusMonitor"), KoinComponent {
    private val contactService: ContactService? by injectNullableNonBinding()
    private val messageService: MessageService? by injectNullableNonBinding()

    override suspend fun run() {
        globalEventFlows.voipCalls
            .collect { event ->
                when (event) {
                    is VoipCallEvent.Started -> {
                        if (event.outgoing) {
                            logger.info("Call to {} started", event.peerIdentity)
                        } else {
                            logger.info("Call from {} started", event.peerIdentity)
                        }
                    }
                    is VoipCallEvent.Finished -> {
                        if (event.outgoing) {
                            logger.info("Call to {} finished", event.peerIdentity)
                        } else {
                            logger.info("Call from {} finished", event.peerIdentity)
                        }
                        saveStatus(
                            identity = event.peerIdentity,
                            isOutbox = event.outgoing,
                            status = VoipStatusDataModel.createFinished(event.callId, event.duration),
                            isRead = true,
                        )
                    }
                    is VoipCallEvent.Rejected -> {
                        if (event.outgoing) {
                            logger.info("Call to {} rejected (reason {})", event.peerIdentity, event.reason)
                        } else {
                            logger.info("Call from {} rejected (reason {})", event.peerIdentity, event.reason)
                        }
                        saveStatus(
                            identity = event.peerIdentity,
                            isOutbox = event.outgoing,
                            status = VoipStatusDataModel.createRejected(event.callId, event.reason),
                            isRead = true,
                        )
                    }
                    is VoipCallEvent.Missed -> {
                        logger.info("Call from {} missed", event.peerIdentity)
                        saveStatus(
                            identity = event.peerIdentity,
                            isOutbox = false,
                            status = VoipStatusDataModel.createMissed(event.callId, event.createdAt),
                            isRead = event.accepted,
                        )
                    }
                    is VoipCallEvent.Aborted -> {
                        logger.info("Call to {} aborted", event.peerIdentity)
                        saveStatus(
                            identity = event.peerIdentity,
                            isOutbox = true,
                            status = VoipStatusDataModel.createAborted(event.callId),
                            isRead = true,
                        )
                    }
                    is VoipCallEvent.Ringing -> Unit
                }
            }
    }

    private fun saveStatus(
        identity: Identity,
        isOutbox: Boolean,
        status: VoipStatusDataModel,
        isRead: Boolean,
    ) {
        if (!isOutbox && identity == identityProvider.getIdentity()) {
            logger.error("Incoming call from {} not targeted at us", identity)
            return
        }

        val receiver = contactService?.createReceiver(identity.value) ?: return
        messageService?.createVoipStatus(status, receiver, isOutbox, isRead)
    }
}
