package ch.threema.app.storagemanagement

import ch.threema.app.storagemanagement.usecases.GetStorageSizeUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val storageManagementFeatureModule = module {
    factory<GetStorageSizeUseCase>()
}
