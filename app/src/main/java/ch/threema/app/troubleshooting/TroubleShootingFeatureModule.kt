package ch.threema.app.troubleshooting

import ch.threema.app.troubleshooting.contacts.ContactsDiagnosticsViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val troubleShootingFeatureModule = module {
    viewModel<ContactsDiagnosticsViewModel>()
}
