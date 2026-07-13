package ch.threema.app.workers

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val workersFeatureModule = module {
    factoryOf(::WorkerStartupScheduler)
    factoryOf(AutoDeleteWorker::Scheduler)
    factoryOf(ContactUpdateWorker::Scheduler)
    factoryOf(GatewayProfilePicturesWorker::Scheduler)
    factoryOf(ShareTargetUpdateWorker::Scheduler)
    factoryOf(WorkSyncWorker::Scheduler)
}
