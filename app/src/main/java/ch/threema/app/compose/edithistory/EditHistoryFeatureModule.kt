package ch.threema.app.compose.edithistory

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val editHistoryFeatureModule = module {
    viewModel<EditHistoryViewModel>()
}
