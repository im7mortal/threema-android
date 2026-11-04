package ch.threema.app.availabilitystatus

import ch.threema.app.availabilitystatus.edit.EditAvailabilityStatusViewModel
import ch.threema.app.usecases.availabilitystatus.UpdateUserAvailabilityStatusUseCase
import ch.threema.app.usecases.availabilitystatus.WatchAllContactAvailabilityStatusesUseCase
import ch.threema.storage.factories.ContactAvailabilityStatusModelFactory
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val availabilityStatusFeatureModule = module {
    viewModel<EditAvailabilityStatusViewModel>()
    factory<UpdateUserAvailabilityStatusUseCase>()
    factory<WatchAllContactAvailabilityStatusesUseCase>()
    single<ContactAvailabilityStatusModelFactory>()
}
