package ch.threema.domain.protocol.connection.data

sealed interface InboundMessage {
    val payloadType: UByte
}

sealed interface OutboundMessage {
    val payloadType: UByte
}
