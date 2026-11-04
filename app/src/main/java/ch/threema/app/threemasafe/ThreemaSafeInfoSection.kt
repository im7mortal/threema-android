package ch.threema.app.threemasafe

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ThreemaSafeInfoSection private constructor(private val jsonObject: JsonObject) {
    fun getJsonObject(): JsonObject = jsonObject

    companion object {
        const val TAG_VERSION = "version"
        const val TAG_DEVICE = "device"

        fun create(version: Int, device: String): ThreemaSafeInfoSection {
            require(version == 1) {
                "only version 1 is supported"
            }

            val jsonObject = buildJsonObject {
                put(TAG_VERSION, version)
                put(TAG_DEVICE, device)
            }
            return ThreemaSafeInfoSection(jsonObject)
        }
    }
}
