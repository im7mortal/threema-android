package ch.threema.app.backuprestore

import ch.threema.app.backuprestore.csv.BackupServiceHelper
import ch.threema.app.backuprestore.csv.RestoreServiceHelper
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val backupRestoreFeatureModule = module {
    factory<RestoreServiceHelper>()
    factory<BackupServiceHelper>()
}
