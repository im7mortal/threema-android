package ch.threema.app.activities.directory

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val directoryFeatureModule = module {
    viewModel<DirectoryViewModel>()
}
