package ch.threema.app.webviews

import ch.threema.app.webviews.usecases.MayOpenUriExternallyUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val webViewsFeatureModule = module {
    factory<WebViewUrlProvider>()
    factory<MayOpenUriExternallyUseCase>()
}
