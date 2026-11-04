package ch.threema.app.location

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val locationFeatureModule = module {
    viewModel<LocationAutocompleteViewModel>()
    factory<PoiRepository>()
    single<MapLibreInitializer>()
}
