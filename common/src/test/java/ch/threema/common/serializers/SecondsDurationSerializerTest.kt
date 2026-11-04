package ch.threema.common.serializers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SecondsDurationSerializerTest {

    @Serializable
    data class Data(
        @Serializable(with = SecondsDurationSerializer::class)
        val data: Duration,
    )

    @Test
    fun `serialize duration as seconds`() {
        assertEquals(
            """{"data":600}""",
            Json.encodeToString(Data(data = 10.minutes)),
        )
    }

    @Test
    fun `deserialize duration from seconds`() {
        assertEquals(
            Data(data = 10.minutes),
            Json.decodeFromString("""{"data":600}"""),
        )
    }
}
