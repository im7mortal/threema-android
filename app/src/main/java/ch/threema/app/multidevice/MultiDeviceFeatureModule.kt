package ch.threema.app.multidevice

import ch.threema.app.multidevice.wizard.LinkNewDeviceWizardViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val multiDeviceFeatureModule = module {
    viewModel<LinkedDevicesViewModel>()
    viewModel<LinkNewDeviceWizardViewModel>()
}
