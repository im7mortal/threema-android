package ch.threema.app.webclient.services.instance.message.updater

import androidx.annotation.AnyThread
import androidx.annotation.StringDef
import androidx.annotation.WorkerThread
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.VoipCallEvent
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.app.webclient.Protocol
import ch.threema.app.webclient.converter.MsgpackObjectBuilder
import ch.threema.app.webclient.converter.VoipStatus
import ch.threema.app.webclient.services.instance.MessageDispatcher
import ch.threema.app.webclient.services.instance.MessageUpdater
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import kotlin.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.msgpack.core.MessagePackException

private val logger = getThreemaLogger("VoipStatusUpdateHandler")

@WorkerThread
class VoipStatusUpdateHandler @AnyThread constructor(
    private val handler: HandlerExecutor,
    private val dispatcher: MessageDispatcher,
) : MessageUpdater(Protocol.SUB_TYPE_VOIP_STATUS), KoinComponent {
    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        TYPE_RINGING,
        TYPE_STARTED,
        TYPE_FINISHED,
        TYPE_REJECTED,
        TYPE_MISSED,
        TYPE_ABORTED,
    )
    private annotation class StatusType

    private val globalEventFlows: GlobalEventFlows by inject()
    private val dispatcherProvider: DispatcherProvider by inject()
    private var coroutineScope: CoroutineScope? = null

    override fun register() {
        coroutineScope?.cancel()
        coroutineScope = CoroutineScope(dispatcherProvider.worker)
        coroutineScope?.launch {
            globalEventFlows.voipCalls.collect { event ->
                onVoipCallEvent(event)
            }
        }
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    override fun unregister() {
        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun onVoipCallEvent(event: VoipCallEvent) {
        when (event) {
            is VoipCallEvent.Ringing -> {
                update(VoipStatus.convertOnRinging(event.peerIdentity.value), TYPE_RINGING)
            }
            is VoipCallEvent.Started -> {
                update(VoipStatus.convertOnStarted(event.peerIdentity.value, event.outgoing), TYPE_STARTED)
            }
            is VoipCallEvent.Finished -> {
                update(
                    VoipStatus.convertOnFinished(
                        event.peerIdentity.value,
                        event.outgoing,
                        event.duration.inWholeSeconds.toInt(),
                    ),
                    TYPE_FINISHED,
                )
            }
            is VoipCallEvent.Rejected -> {
                update(VoipStatus.convertOnRejected(event.peerIdentity.value, !event.outgoing, event.reason ?: 0), TYPE_REJECTED)
            }
            is VoipCallEvent.Missed -> {
                update(VoipStatus.convertOnMissed(event.peerIdentity.value), TYPE_MISSED)
            }
            is VoipCallEvent.Aborted -> {
                update(VoipStatus.convertOnAborted(event.peerIdentity.value), TYPE_ABORTED)
            }
        }
    }

    private fun update(data: MsgpackObjectBuilder, @StatusType type: String) {
        handler.post {
            try {
                logger.info("Sending voip status update ({})", type)
                val args = MsgpackObjectBuilder().put("type", type)
                send(dispatcher, data, args)
            } catch (e: MessagePackException) {
                logger.error("Exception", e)
            }
        }
    }

    companion object {
        private const val TYPE_RINGING = "ringing"
        private const val TYPE_STARTED = "started"
        private const val TYPE_FINISHED = "finished"
        private const val TYPE_REJECTED = "rejected"
        private const val TYPE_MISSED = "missed"
        private const val TYPE_ABORTED = "aborted"
    }
}
