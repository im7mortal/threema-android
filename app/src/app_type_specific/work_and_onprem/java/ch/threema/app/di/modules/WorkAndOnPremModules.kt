package ch.threema.app.di.modules

import android.content.Context
import android.content.RestrictionsManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import ch.threema.app.di.emptyModule
import ch.threema.app.restrictions.AppRestrictionProvider
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.restrictions.AppRestrictionServiceImpl
import ch.threema.app.restrictions.MdmSettingsStore
import ch.threema.app.restrictions.WorkAppRestrictionProvider
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DoNotDisturbUtil
import ch.threema.app.utils.WorkDoNotDisturbUtil
import ch.threema.app.work.LegacyWorkPropertiesUpdater
import ch.threema.app.work.LegacyWorkPropertiesUpdaterImpl
import ch.threema.app.work.WorkSync
import ch.threema.app.work.WorkSyncImpl
import ch.threema.app.work.workproperties.WorkPropertiesClient
import ch.threema.app.work.workproperties.WorkPropertiesClientImpl
import ch.threema.domain.libthreema.LibthreemaHttpClient
import ch.threema.domain.models.WorkClientInfo
import java.util.Locale
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

/**
 * Components which are only relevant for Work and OnPrem builds
 */
val workAndOnPremModule = module {
    factory<LegacyWorkPropertiesUpdaterImpl>() bind LegacyWorkPropertiesUpdater::class

    factory<WorkPropertiesClient> {
        WorkPropertiesClientImpl(
            clientInfo = get(),
            httpClient = LibthreemaHttpClient(
                okHttpClient = get(),
            ),
            serverAddressProvider = get(),
            userService = get(),
            licenseService = get(),
        )
    }

    factory<WorkClientInfo> { getWorkClientInfo() }

    factory<WorkDoNotDisturbUtil>() bind DoNotDisturbUtil::class

    factory<WorkSyncImpl>() bind WorkSync::class

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
        )
    }
}

private fun getWorkClientInfo(): WorkClientInfo =
    WorkClientInfo(
        appVersion = ConfigUtils.getAppVersion(),
        appLocale = Locale.getDefault().toString(),
        deviceModel = Build.MODEL,
        osVersion = Build.VERSION.RELEASE,
        workFlavor = workFlavor,
    )

val onlyPrivateModule = emptyModule()
