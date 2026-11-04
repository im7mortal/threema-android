package ch.threema.app.camera

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val cameraFeatureModule = module {
    viewModel<CameraViewModel>()
}
