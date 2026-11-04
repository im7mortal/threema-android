package ch.threema.common.serializers

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class UUIDSerializerTest {

    @Serializable
    data class Data(
        @Serializable(with = UUIDSerializer::class)
        val data: UUID,
    )

    @Test
    fun `serialize uuid`() {
        assertEquals(
            """{"data":"1af6f475-fd92-4c59-83c0-925923c70279"}""",
            Json.encodeToString(Data(data = UUID.fromString("1af6f475-fd92-4c59-83c0-925923c70279"))),
        )
    }

    @Test
    fun `deserialize uuid`() {
        assertEquals(
            Data(data = UUID.fromString("1af6f475-fd92-4c59-83c0-925923c70279")),
            Json.decodeFromString("""{"data":"1af6f475-fd92-4c59-83c0-925923c70279"}"""),
        )
    }
}
