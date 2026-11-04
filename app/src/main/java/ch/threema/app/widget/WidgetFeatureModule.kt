package ch.threema.app.widget

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val widgetFeatureModule = module {
    factory<WidgetUpdater>()
    factory<WidgetUpdaterMonitor>()
}
