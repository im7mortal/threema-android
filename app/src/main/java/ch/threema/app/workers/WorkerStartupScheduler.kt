package ch.threema.app.workers

import ch.threema.app.GlobalAppState
import ch.threema.app.files.TempFilesCleanupWorker
import ch.threema.app.logging.DebugLogHelper
import ch.threema.app.preference.service.PreferenceService
import ch.threema.data.IdentityProvider

class WorkerStartupScheduler(
    private val identityProvider: IdentityProvider,
    private val preferenceService: PreferenceService,
    private val globalAppState: GlobalAppState,
    private val workSyncWorkerScheduler: WorkSyncWorker.Scheduler,
    private val contactUpdateWorkerScheduler: ContactUpdateWorker.Scheduler,
    private val autoDeleteWorkerScheduler: AutoDeleteWorker.Scheduler,
    private val shareTargetUpdateWorkerScheduler: ShareTargetUpdateWorker.Scheduler,
    private val debugLogHelper: DebugLogHelper,
    private val gatewayProfilePicturesWorkerScheduler: GatewayProfilePicturesWorker.Scheduler,
    private val tempFilesCleanupWorkerScheduler: TempFilesCleanupWorker.Scheduler,
) {
    fun scheduleWorkers() {
        workSyncWorkerScheduler.schedulePeriodicWorkSync()
        contactUpdateWorkerScheduler.schedulePeriodicSync()
        if (preferenceService.isDirectShare()) {
            shareTargetUpdateWorkerScheduler.schedulePeriodicUpdate()
        }
        autoDeleteWorkerScheduler.scheduleAutoDelete()
        debugLogHelper.updateDebugLogFileDeletionSchedule()
        gatewayProfilePicturesWorkerScheduler.schedulePeriodicSync()
        if (identityProvider.hasIdentity() && !globalAppState.isRestoreRunning) {
            // Don't start cleanup if a backup restore is running to avoid interfering with it
            tempFilesCleanupWorkerScheduler.schedulePeriodicCleanup()
        }
    }
}
