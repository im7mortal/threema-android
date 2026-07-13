package ch.threema.app.shortcuts

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val shortcutsFeatureModule = module {
    singleOf(::ShortcutsUpdaterMonitor)
}
