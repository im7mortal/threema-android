package ch.threema.app.work

@Suppress("unused")
class NoOpWorkSync : WorkSync {
    override suspend fun run(forceAppLogoFetch: Boolean) =
        WorkSync.WorkSyncResult.Success
}
