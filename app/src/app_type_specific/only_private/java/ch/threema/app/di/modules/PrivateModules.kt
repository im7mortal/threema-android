package ch.threema.app.di.modules

import ch.threema.app.di.emptyModule
import ch.threema.app.restrictions.AppRestrictionProvider
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.restrictions.NoOpAppRestrictionService
import ch.threema.app.restrictions.NullAppRestrictionProvider
import ch.threema.app.utils.DoNotDisturbUtil
import ch.threema.app.utils.PrivateDoNotDisturbUtil
import ch.threema.app.work.LegacyWorkPropertiesUpdater
import ch.threema.app.work.NoOpLegacyWorkPropertiesUpdater
import ch.threema.app.work.NoOpWorkSync
import ch.threema.app.work.WorkSync
import ch.threema.app.work.workproperties.NoOpWorkPropertiesClient
import ch.threema.app.work.workproperties.WorkPropertiesClient
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

/**
 * Components which are only relevant for Private builds
 */
val onlyPrivateModule = module {
    factory<NoOpLegacyWorkPropertiesUpdater>() bind LegacyWorkPropertiesUpdater::class

    factory<WorkPropertiesClient> {
        NoOpWorkPropertiesClient()
    }

    factory<DoNotDisturbUtil> {
        PrivateDoNotDisturbUtil(
            identityProvider = get(),
            timeProvider = get(),
        )
    }

    factory<WorkSync> {
        NoOpWorkSync()
    }

    single<AppRestrictionProvider> {
        NullAppRestrictionProvider()
    }
    single<AppRestrictionService> {
        NoOpAppRestrictionService()
    }
}

val onlyWorkModule = emptyModule()
val workAndOnPremModule = emptyModule()
