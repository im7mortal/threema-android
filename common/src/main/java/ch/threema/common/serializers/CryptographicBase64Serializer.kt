package ch.threema.common.serializers

import ch.threema.common.Base64
import ch.threema.common.models.CryptographicByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class CryptographicBase64Serializer : KSerializer<CryptographicByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("CryptographicBase64", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CryptographicByteArray) {
        encoder.encodeString(Base64.encode(value.value))
    }

    override fun deserialize(decoder: Decoder): CryptographicByteArray =
        CryptographicByteArray(Base64.decode(decoder.decodeString()))
}
