package ch.threema.app.work

@Suppress("unused")
class NoOpLegacyWorkPropertiesUpdater : LegacyWorkPropertiesUpdater {
    override fun runAsynchronously() {
        // Nothing to do
    }

    override suspend fun run(): LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult =
        LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult.Success
}
