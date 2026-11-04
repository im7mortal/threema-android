package ch.threema.app.webclient

import ch.threema.app.webclient.activities.SessionsViewModel
import ch.threema.app.webclient.usecases.DetectBrowserUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val webclientFeatureModule = module {
    viewModel<SessionsViewModel>()
    factory<DetectBrowserUseCase>()
}
