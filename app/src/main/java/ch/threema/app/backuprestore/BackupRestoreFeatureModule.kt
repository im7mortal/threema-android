package ch.threema.app.backuprestore

import ch.threema.app.backuprestore.csv.RestoreServiceHelper
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val backupRestoreFeatureModule = module {
    factoryOf(::RestoreServiceHelper)
}
