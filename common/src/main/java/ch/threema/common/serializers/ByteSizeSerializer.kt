package ch.threema.common.serializers

import ch.threema.common.ByteSize
import ch.threema.common.bytes
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class ByteSizeSerializer : KSerializer<ByteSize> {
    override val descriptor = PrimitiveSerialDescriptor("ByteSize", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: ByteSize) {
        encoder.encodeLong(value.bytes)
    }

    override fun deserialize(decoder: Decoder): ByteSize =
        decoder.decodeLong().bytes
}
