package ch.threema.app.services

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val servicesFeatureModule = module {
    factory<PassphraseService.Starter>()
    factory<ThreemaPushService.Starter>()
}
