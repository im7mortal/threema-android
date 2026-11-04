package ch.threema.app.applogo

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val appLogoFeatureModule = module {
    single<AppLogoProvider>()
    factory<UpdateAppLogoUseCase>()
}
