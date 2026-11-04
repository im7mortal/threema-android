package ch.threema.app.work

interface LegacyWorkPropertiesUpdater {
    enum class UpdateLegacyWorkPropertiesResult {
        Success,
        MissingIdentity,
        MissingCredentials,
        Failed,
    }

    /**
     * Updates the legacy work properties in a new coroutine. Preferably, we should use [run] instead. This exists mainly for legacy Java code.
     */
    fun runAsynchronously()

    /**
     * Update the legacy work properties.
     */
    suspend fun run(): UpdateLegacyWorkPropertiesResult
}
