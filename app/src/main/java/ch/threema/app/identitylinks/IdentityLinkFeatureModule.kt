package ch.threema.app.identitylinks

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val identityLinkFeatureModule = module {
    factory<VerifyMobileNumberUseCase>()
}
