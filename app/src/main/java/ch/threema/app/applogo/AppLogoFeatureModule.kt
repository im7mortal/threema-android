package ch.threema.app.applogo

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appLogoFeatureModule = module {
    singleOf(::AppLogoProvider)
    factoryOf(::UpdateAppLogoUseCase)
}
