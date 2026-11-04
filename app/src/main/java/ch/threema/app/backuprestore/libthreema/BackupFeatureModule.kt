package ch.threema.app.backuprestore.libthreema

import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val backupFeatureModule = module {
    // Create backup
    single<BackupCreateManager>()
    single<BackupCreateStateManager>()
    factory<BackupCreateJob.Creator>()
    factory<BackupCreateService.Scheduler>()

    // Restore backup
    single<BackupRestoreManager>()
    single<BackupRestoreStateManager>() bind BackupRestoreStateProvider::class
    viewModel<BackupRestoreViewModel>()
    factory<BackupRestoreJob.Creator>()
    factory<BackupRestoreService.Scheduler>()
}
