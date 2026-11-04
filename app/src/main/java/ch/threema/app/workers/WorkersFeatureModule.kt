package ch.threema.app.workers

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val workersFeatureModule = module {
    factory<WorkerStartupScheduler>()
    factory<AutoDeleteWorker.Scheduler>()
    factory<ContactUpdateWorker.Scheduler>()
    factory<GatewayProfilePicturesWorker.Scheduler>()
    factory<ShareTargetUpdateWorker.Scheduler>()
    factory<WorkSyncWorker.Scheduler>()
    factory<AppRestartWorker.Scheduler>()
}
