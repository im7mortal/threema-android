package ch.threema.app.restrictions

import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.api.work.WorkMDMSettings
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import kotlin.collections.mapValues
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private val logger = getThreemaLogger("MdmSettingsStore")

class MdmSettingsStore(
    private val encryptedPreferenceStore: EncryptedPreferenceStore,
) {
    /**
     * @throws MasterKeyLockedException
     */
    fun storeWorkMDMSettings(settings: WorkMDMSettings) {
        val serializable = SerializableWorkMDMSettings(
            override = settings.override,
            parameters = settings.parameters.mapValues { (_, value) ->
                when (value) {
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is String -> JsonPrimitive(value)
                    else -> null
                }
            },
        )
        encryptedPreferenceStore.save(PREFERENCE_KEY, Json.encodeToString(serializable))
    }

    /**
     * @throws MasterKeyLockedException
     */
    fun getWorkMDMSettings(): WorkMDMSettings? =
        if (encryptedPreferenceStore.containsKey(PREFERENCE_KEY)) {
            encryptedPreferenceStore.getString(PREFERENCE_KEY)
        } else {
            logger.warn("No MDM settings stored")
            null
        }
            ?.let { jsonString ->
                try {
                    val serializable = Json.decodeFromString<SerializableWorkMDMSettings>(jsonString)
                    WorkMDMSettings(
                        override = serializable.override,
                        parameters = serializable.parameters.mapValues { (_, value) ->
                            if (value?.isString == true) {
                                value.contentOrNull
                            } else {
                                value?.booleanOrNull
                                    ?: value?.intOrNull
                                    ?: value?.longOrNull
                                    ?: value?.floatOrNull
                                    ?: value?.doubleOrNull
                            }
                        },
                    )
                } catch (_: IllegalArgumentException) {
                    null
                }
            }

    @Serializable
    private data class SerializableWorkMDMSettings(
        @JvmField
        val override: Boolean = false,
        @JvmField
        val parameters: Map<String, JsonPrimitive?> = mapOf(),
    )

    companion object {
        private const val PREFERENCE_KEY = "wrk_app_restriction"
    }
}
