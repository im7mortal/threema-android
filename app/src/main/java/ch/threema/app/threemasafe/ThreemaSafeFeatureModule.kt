package ch.threema.app.threemasafe

import ch.threema.app.threemasafe.usecases.CheckBadPasswordUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val threemaSafeFeatureModule = module {
    factory<ThreemaSafeMDMConfig> {
        @Suppress("DEPRECATION")
        ThreemaSafeMDMConfig.getInstance()
    }
    factoryOf(::CheckBadPasswordUseCase)
}
