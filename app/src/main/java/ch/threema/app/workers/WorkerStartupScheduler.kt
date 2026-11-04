package ch.threema.app.workers

import ch.threema.app.files.TempFilesCleanupWorker
import ch.threema.app.logging.DebugLogHelper
import ch.threema.app.preference.service.PreferenceService

class WorkerStartupScheduler(
    private val preferenceService: PreferenceService,
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
        tempFilesCleanupWorkerScheduler.schedulePeriodicCleanup()
    }
}
