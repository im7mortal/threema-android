package ch.threema.app.pinlock

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val pinLockFeatureModule = module {
    viewModel<PinLockViewModel>()
    factory<PinLockDeadlineManager>()
    factory<LockoutTimeoutProvider>()
}
