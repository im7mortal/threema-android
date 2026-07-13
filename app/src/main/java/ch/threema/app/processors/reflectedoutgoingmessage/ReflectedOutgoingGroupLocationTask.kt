package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.location.GroupLocationMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.LocationDataModel
import ch.threema.storage.models.data.MessageContentsType
import ch.threema.storage.models.group.GroupMessageModel
import java.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectedOutgoingGroupLocationTask")

internal class ReflectedOutgoingGroupLocationTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupLocationMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupLocationMessage.fromReflected(outgoingMessage),
    type = CspE2eMessageType.GROUP_LOCATION,
    serviceManager = serviceManager,
),
    KoinComponent {
    private val globalEventBuses: GlobalEventBuses by inject()
    private val messageService by lazy { serviceManager.messageService }

    override fun processOutgoingMessage() {
        check(outgoingMessage.conversation.hasGroup()) {
            "The message does not have a group identity set"
        }

        messageService.getMessageModelByApiMessageIdAndReceiver(message.messageId.toString(), messageReceiver)?.run {
            // It is possible that a message gets reflected twice when the reflecting task gets restarted.
            logger.info("Skipping message {} as it already exists.", message.messageId)
            return
        }

        val groupMessageModel: GroupMessageModel = messageReceiver.createLocalModel(
            MessageType.LOCATION,
            MessageContentsType.LOCATION,
            Instant.ofEpochMilli(outgoingMessage.createdAt),
        )
        groupMessageModel.locationData = LocationDataModel(
            latitude = message.latitude,
            longitude = message.longitude,
            accuracy = message.accuracy,
            poi = message.poi,
        )
        messageService.save(groupMessageModel)
        globalEventBuses.messages.emit(MessageEvent.NewMessage(groupMessageModel))
    }
}
