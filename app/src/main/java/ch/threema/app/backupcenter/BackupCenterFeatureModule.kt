package ch.threema.app.backupcenter

import ch.threema.app.backupcenter.usecases.CheckThreemaSafeAvailableUseCase
import ch.threema.app.backupcenter.usecases.GetBackupLocationUseCase
import ch.threema.app.backupcenter.usecases.GetBackupProgressUseCase
import ch.threema.app.backupcenter.usecases.GetOrGenerateBackupPasswordUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val backupCenterFeatureModule = module {
    viewModel<BackupCenterViewModel>()
    viewModel<LocalDataBackupViewModel>()
    viewModel<CreateLocalDataBackupViewModel>()
    factory<CheckThreemaSafeAvailableUseCase>()
    factory<GetOrGenerateBackupPasswordUseCase>()
    factory<GetBackupProgressUseCase>()
    factory<GetBackupLocationUseCase>()
}
