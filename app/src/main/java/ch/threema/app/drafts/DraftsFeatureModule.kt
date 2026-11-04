package ch.threema.app.drafts

import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val draftsFeatureModule = module {
    single<DraftManagerImpl>() bind DraftManager::class
}
