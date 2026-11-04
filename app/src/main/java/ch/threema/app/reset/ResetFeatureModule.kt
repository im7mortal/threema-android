package ch.threema.app.reset

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val resetFeatureModule = module {
    factory<ResetAppTask>()
    factory<DeleteAllContactsHelper>()
}
