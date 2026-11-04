package ch.threema.app.shortcuts

import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val shortcutsFeatureModule = module {
    single<ShortcutsUpdaterMonitor>()
}
