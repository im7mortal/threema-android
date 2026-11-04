package ch.threema.app.applock

import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val appLockFeatureModule = module {
    single<AppLockUtil>()
    single<AppLockNotificationUpdaterMonitor>()
}
