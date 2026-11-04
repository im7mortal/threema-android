package ch.threema.app.di.modules

import ch.threema.app.BuildConfig
import ch.threema.app.di.Qualifiers
import ch.threema.app.di.emptyModule
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.onprem.OnPremCertPinning
import ch.threema.app.onprem.OnPremConfigFetcherProvider
import ch.threema.app.onprem.OnPremServerAddressProvider
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.startup.RemoteSecretProtectionStateMonitor
import ch.threema.domain.libthreema.LibthreemaHttpClient
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.models.WorkClientInfo
import ch.threema.domain.onprem.OnPremConfigParser
import ch.threema.domain.onprem.OnPremConfigStore
import ch.threema.domain.onprem.OnPremConfigVerifier
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.api.APIAuthenticator
import ch.threema.localcrypto.RemoteSecretClient
import ch.threema.localcrypto.RemoteSecretManager
import ch.threema.localcrypto.RemoteSecretManagerImpl
import ch.threema.localcrypto.RemoteSecretMonitor
import okhttp3.OkHttpClient
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

/**
 * Components which are only relevant for OnPrem builds
 */
val onlyOnPremModule = module {
    factory<APIAuthenticator> {
        APIAuthenticator {
            with(get<PreferenceService>()) {
                val username = getLicenseUsername() ?: return@APIAuthenticator null
                val password = getLicensePassword() ?: return@APIAuthenticator null
                UserCredentials(username, password)
            }
        }
    }

    single<ServerAddressProvider> {
        OnPremServerAddressProvider(
            fetcherProvider = { get<OnPremConfigFetcherProvider>().getOnPremConfigFetcher() },
        )
    }

    factory<OnPremConfigStore> {
        OnPremConfigStore(
            baseDirectory = get<AppDirectoryProvider>().appDataDirectory,
            timeProvider = get(),
            onPremConfigParser = get(),
            onPremConfigVerifier = get(),
        )
    }

    single<OnPremConfigFetcherProvider> {
        val okHttpBaseClient = get<OkHttpClient>(qualifier = Qualifiers.okHttpBase)
        OnPremConfigFetcherProvider(
            preferenceService = get(),
            onPremConfigParser = get(),
            onPremConfigStore = get(),
            onPremConfigVerifier = get(),
            timeProvider = get(),
            pinnedOkHttpClient = get<OnPremCertPinning>().createOkHttpClientWithoutRegularOppfFetching(
                baseClient = okHttpBaseClient,
                requireCertificatePinning = false,
            ),
            unpinnedOkHttpClient = okHttpBaseClient,
        )
    }

    factory<OnPremConfigParser>()

    single<OnPremConfigVerifier> {
        OnPremConfigVerifier(BuildConfig.ONPREM_CONFIG_TRUSTED_PUBLIC_KEYS)
    }

    single<OnPremCertPinning> {
        OnPremCertPinning(
            getOnPremConfigFetcherProvider = { get() },
            getOnPremConfigStore = { get() },
        )
    }

    single<OkHttpClient> {
        val baseClient = get<OkHttpClient>(qualifier = Qualifiers.okHttpBase)
        get<OnPremCertPinning>().createDefaultOkHttpClient(baseClient)
    }

    factory<RemoteSecretMonitor>()

    factory {
        RemoteSecretClient(
            clientInfo = get(),
            httpClient = LibthreemaHttpClient(
                // here we need to use the best-effort OkHttp client, as we might end up in a situation where
                // the certificate pins of the stored OPPF are no longer valid, and we can only recover from this
                // by fetching the OPPF from the fallback URL.
                okHttpClient = get<OnPremCertPinning>().createOkHttpClientWithoutRegularOppfFetching(
                    baseClient = get<OkHttpClient>(qualifier = Qualifiers.okHttpBase),
                ),
            ),
        )
    }

    factory<RemoteSecretManager> {
        RemoteSecretManagerImpl(
            remoteSecretClient = get(),
            remoteSecretMonitor = get(),
            shouldUseRemoteSecretProtection = {
                get<AppRestrictions>().isRemoteSecretEnabled()
            },
            getWorkServerBaseUrl = {
                get<OnPremConfigStore>().get()?.work?.url
                    ?: error("cannot monitor remote secret, no stored OPPF found")
            },
        )
    }

    factory<RemoteSecretProtectionStateMonitor>()
}

val workFlavor = WorkClientInfo.WorkFlavor.ON_PREM

val privateAndWorkModule = emptyModule()
val onlyWorkModule = emptyModule()
