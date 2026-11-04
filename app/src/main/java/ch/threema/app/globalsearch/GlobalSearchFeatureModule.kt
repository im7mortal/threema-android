package ch.threema.app.globalsearch

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val globalSearchFeatureModule = module {
    viewModel<GlobalSearchViewModel>()
}
