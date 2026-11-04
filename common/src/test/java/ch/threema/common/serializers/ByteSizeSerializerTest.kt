package ch.threema.common.serializers

import ch.threema.common.ByteSize
import ch.threema.common.bytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ByteSizeSerializerTest {

    @Serializable
    data class Data(
        @Serializable(with = ByteSizeSerializer::class)
        val data: ByteSize,
    )

    @Test
    fun `serialize byte size`() {
        assertEquals(
            """{"data":25}""",
            Json.encodeToString(Data(data = 25.bytes)),
        )
    }

    @Test
    fun `deserialize byte size`() {
        assertEquals(
            Data(data = 25.bytes),
            Json.decodeFromString("""{"data":25}"""),
        )
    }
}
