package ch.threema.app.libthreema.extensions

import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.libthreema.IncomingMessageDirection as LibthreemaIncomingMessageDirection
import ch.threema.libthreema.MessageDirection as LibthreemaMessageDirection
import ch.threema.libthreema.MessageOverrides as LibthreemaMessageOverrides
import ch.threema.libthreema.MessageOverridesDeliveryReceiptPolicy as LibthreemaMessageOverridesDeliveryReceiptPolicy
import ch.threema.libthreema.MessageStarMarker as LibthreemaMessageStarMarker
import ch.threema.libthreema.OutgoingMessageDirection as LibthreemaOutgoingMessageDirection
import ch.threema.storage.models.MessageModel

fun MessageModel.getLibthreemaMessageDirection(): LibthreemaMessageDirection = if (isOutbox) {
    LibthreemaMessageDirection.Outgoing(
        v1 = LibthreemaOutgoingMessageDirection(
            sentAt = postedAt?.toEpochMilli()?.toULong(),
            deliveredAt = deliveredAt?.toEpochMilli()?.toULong(),
        ),
    )
} else {
    LibthreemaMessageDirection.Incoming(
        v1 = LibthreemaIncomingMessageDirection(
            receivedAt = deliveredAt?.toEpochMilli()?.toULong() ?: 0u, // TODO(SE-609),
            overrides = LibthreemaMessageOverrides(
                deliveryReceiptPolicy = if (hasMessageFlag(ProtocolDefines.MESSAGE_FLAG_NO_DELIVERY_RECEIPTS)) {
                    LibthreemaMessageOverridesDeliveryReceiptPolicy.DONT_SEND
                } else {
                    LibthreemaMessageOverridesDeliveryReceiptPolicy.DEFAULT
                },
            ),
        ),
    )
}

fun MessageModel.getLibthreemaMessageStarMarker(): LibthreemaMessageStarMarker =
    if (isStarred) LibthreemaMessageStarMarker.STAR else LibthreemaMessageStarMarker.NONE
