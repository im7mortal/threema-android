package ch.threema.app.messagedetails

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val messageDetailsFeatureModule = module {
    viewModel<MessageDetailsViewModel>()
}
