package ch.threema.app.mediagallery

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val mediaGalleryFeatureModule = module {
    factory<MediaGalleryRepository>()
    viewModel<MediaGalleryViewModel>()
}
