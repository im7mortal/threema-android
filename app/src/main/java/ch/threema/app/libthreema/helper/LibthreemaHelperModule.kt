package ch.threema.app.libthreema.helper

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val libthreemaHelperModule = module {
    factory { BatchSizeProvider() }
    factory<MessageStateDatabaseReader>()
    factory<MessageStateDatabaseWriter>()
}
