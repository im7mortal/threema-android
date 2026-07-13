package ch.threema.app.troubleshooting

import ch.threema.app.troubleshooting.contacts.ContactsDiagnosticsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val troubleShootingFeatureModule = module {
    viewModelOf(::ContactsDiagnosticsViewModel)
}
