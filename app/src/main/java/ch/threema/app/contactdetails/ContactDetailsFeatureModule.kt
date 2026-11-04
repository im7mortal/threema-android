package ch.threema.app.contactdetails

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val contactDetailsFeatureModule = module {
    viewModel<ContactDetailViewModel>()
}
