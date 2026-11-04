package ch.threema.common.serializers

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class InstantSerializerTest {

    @Serializable
    data class Data(
        @Serializable(with = InstantSerializer::class)
        val data: Instant,
    )

    @Test
    fun `serialize instant`() {
        assertEquals(
            """{"data":123}""",
            Json.encodeToString(Data(data = Instant.ofEpochMilli(123))),
        )
    }

    @Test
    fun `deserialize instant`() {
        assertEquals(
            Data(data = Instant.ofEpochMilli(123)),
            Json.decodeFromString("""{"data":123}"""),
        )
    }
}
