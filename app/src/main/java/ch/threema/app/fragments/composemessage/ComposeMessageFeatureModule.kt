package ch.threema.app.fragments.composemessage

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val composeMessageFeatureModule = module {
    viewModel<ComposeMessageViewModel>()
    factory<ComposeMessageFragmentUtils>()
}
