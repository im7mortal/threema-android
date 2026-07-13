package ch.threema.domain.protocol.api.work

/**
 * @param override if true, parameters set here override those set by an AppConfig-style MDM
 */
data class WorkMDMSettings(
    @JvmField
    val override: Boolean = false,
    @JvmField
    val parameters: Map<String, Any?> = mapOf(),
)
