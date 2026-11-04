package ch.threema.app.dev

import ch.threema.app.dev.preference.ContentCreator
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val devFeatureModule = module {
    factory<ContentCreator>()
}
