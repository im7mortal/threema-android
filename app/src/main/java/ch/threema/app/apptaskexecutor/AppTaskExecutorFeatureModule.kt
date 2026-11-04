package ch.threema.app.apptaskexecutor

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val appTaskExecutorFeatureModule = module {
    single<AppTaskExecutor>()
    factory<AppTaskPersistenceProvider>()
}
