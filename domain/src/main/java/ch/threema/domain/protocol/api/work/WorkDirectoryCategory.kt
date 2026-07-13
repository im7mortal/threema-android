package ch.threema.domain.protocol.api.work

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WorkDirectoryCategory(
    @JvmField
    val id: String? = null,
    @JvmField
    val name: String? = null,
) {
    fun serialize(): String =
        Json.encodeToString(this)

    companion object {
        @JvmStatic
        fun deserialize(serialized: String): WorkDirectoryCategory =
            Json.decodeFromString(serialized)
    }
}
