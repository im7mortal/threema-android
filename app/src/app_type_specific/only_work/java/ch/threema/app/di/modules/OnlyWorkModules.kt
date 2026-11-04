package ch.threema.app.di.modules

import ch.threema.domain.models.WorkClientInfo
import org.koin.dsl.module

/**
 * Components which are only relevant for Work (but not OnPrem) builds
 */
val onlyWorkModule = module {
    // empty for now, may be extended in the future
}

val workFlavor = WorkClientInfo.WorkFlavor.WORK
