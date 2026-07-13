package ch.threema.app.backupcenter

import ch.threema.app.backupcenter.usecases.CheckThreemaSafeAvailableUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val backupCenterFeatureModule = module {
    viewModelOf(::BackupCenterViewModel)
    viewModelOf(::LocalDataBackupViewModel)
    viewModelOf(::CreateLocalDataBackupViewModel)
    factoryOf(::CheckThreemaSafeAvailableUseCase)
}
