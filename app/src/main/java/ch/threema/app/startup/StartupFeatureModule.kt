package ch.threema.app.startup

import ch.threema.app.BuildFlavor
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.services.RemoteSecretMonitorService
import ch.threema.app.utils.RuntimeUtil
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val startupFeatureModule = module {
    viewModel<RemoteSecretProtectionUpdateViewModel>()
    factory<MasterKeyEventMonitor>()

    factory<AppProcessLifecycleObserver> {
        AppProcessLifecycleObserver(
            dispatcherProvider = get(),
            reloadAppRestrictions = {
                if (BuildFlavor.IS_WORK) {
                    RuntimeUtil.runOnWorkerThread {
                        get<AppRestrictionService>().reload()
                    }
                }
            },
        )
    }

    factory<RemoteSecretMonitorService.Scheduler>()

    single<RemoteSecretMonitorRetryController>()
    factory<RemoteSecretProtectionUpdater>()
}
