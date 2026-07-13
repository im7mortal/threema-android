package ch.threema.app.voip

import ch.threema.app.voip.activities.CallActivityHelper
import ch.threema.app.voip.util.CallIdGenerator
import ch.threema.app.voip.viewmodel.GroupCallViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val voipFeatureModule = module {
    factoryOf(::CallActivityHelper)
    factoryOf(::CallIdGenerator)
    viewModelOf(::GroupCallViewModel)

    singleOf(::VoipCallStatusMonitor)
}
