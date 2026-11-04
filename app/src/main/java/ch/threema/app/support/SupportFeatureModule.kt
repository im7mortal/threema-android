package ch.threema.app.support

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val supportFeatureModule = module {
    viewModel<ContactSupportViewModel>()
}
