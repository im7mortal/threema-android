package ch.threema.app.restrictions

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val appRestrictionsFeatureModule = module {
    factory<ApplyAppRestrictionsWorker.Scheduler>()
    single<AppRestrictions>()
}
