package ch.threema.common.serializers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FileSerializerTest {

    @Serializable
    data class Data(
        @Serializable(with = FileSerializer::class)
        val data: File,
    )

    @Test
    fun `serialize file`() {
        assertEquals(
            """{"data":"/some/file"}""",
            Json.encodeToString(Data(data = File("/some/file"))),
        )
    }

    @Test
    fun `deserialize file`() {
        assertEquals(
            Data(data = File("/some/file")),
            Json.decodeFromString("""{"data":"/some/file"}"""),
        )
    }
}
