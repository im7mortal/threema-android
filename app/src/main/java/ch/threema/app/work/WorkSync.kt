package ch.threema.app.work

interface WorkSync {
    enum class WorkSyncResult {
        Success,
        MissingCredentials,
        Fetch2Failed,
        Fetch2Unauthorized,
        ContactUpdateFailed,
    }

    /**
     * Runs the work sync. If [forceAppLogoFetch] is set, the app logo is fetched from the server even if it did not expire yet.
     *
     * Note that this does not await special modes. It is the responsibility of the caller to determine whether they should be awaited.
     */
    suspend fun run(
        forceAppLogoFetch: Boolean,
    ): WorkSyncResult
}
