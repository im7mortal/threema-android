package ch.threema.app.voip

import ch.threema.app.voip.activities.CallActivityHelper
import ch.threema.app.voip.util.CallIdGenerator
import ch.threema.app.voip.viewmodel.GroupCallViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val voipFeatureModule = module {
    factory<CallActivityHelper>()
    factory<CallIdGenerator>()
    viewModel<GroupCallViewModel>()

    single<VoipCallStatusMonitor>()
}
