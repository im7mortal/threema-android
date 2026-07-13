package ch.threema.app.restrictions

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.core.content.getSystemService
import ch.threema.app.utils.ConfigUtils
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appRestrictionsFeatureModule = module {
    factoryOf(ApplyAppRestrictionsWorker::Scheduler)

    singleOf(::AppRestrictions)
    if (ConfigUtils.isWorkBuild()) {
        single<AppRestrictionProvider> {
            WorkAppRestrictionProvider(
                getRestrictions = {
                    get<AppRestrictionService>()
                        .appRestrictions
                        ?.takeUnless { it.isEmpty }
                },
            )
        }
        single<AppRestrictionService> {
            AppRestrictionServiceImpl(
                appContext = get(),
                mdmSettingsStore = MdmSettingsStore(
                    encryptedPreferenceStore = get(),
                ),
                applyAppRestrictionsWorkerScheduler = get(),
                getExternalMdmParameters = {
                    get<Context>().getSystemService<RestrictionsManager>()
                        ?.applicationRestrictions
                        ?: Bundle()
                },
                workInfoUpdater = WorkInfoUpdater(
                    identityProvider = get(),
                ),
            )
        }
    } else {
        single<AppRestrictionProvider> {
            NullAppRestrictionProvider()
        }
        single<AppRestrictionService> {
            NoOpAppRestrictionService()
        }
    }
}
