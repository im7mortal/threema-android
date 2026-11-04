package ch.threema.common.serializers

import ch.threema.common.models.CryptographicByteArray
import ch.threema.testhelpers.cryptographicByteArrayOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CryptographicBase64SerializerTest {

    @Serializable
    data class Data(
        @Serializable(with = CryptographicBase64Serializer::class)
        val data: CryptographicByteArray,
    )

    @Test
    fun `serialize cryptographic byte array`() {
        assertEquals(
            """{"data":"AQID/w=="}""",
            Json.encodeToString(Data(data = cryptographicByteArrayOf(1, 2, 3, 0xff.toByte()))),
        )
    }

    @Test
    fun `deserialize cryptographic byte array`() {
        assertEquals(
            Data(data = cryptographicByteArrayOf(1, 2, 3, 0xff.toByte())),
            Json.decodeFromString("""{"data":"AQID/w=="}"""),
        )
    }
}
