package ch.threema.app.di.modules

import ch.threema.app.di.Qualifiers
import ch.threema.app.di.emptyModule
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.DefaultServerAddressProvider
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.localcrypto.NoOpRemoteSecretManagerImpl
import ch.threema.localcrypto.RemoteSecretManager
import okhttp3.OkHttpClient
import org.koin.dsl.module

/**
 * Components which are only relevant for Private and Work (but not OnPrem) builds
 */
val privateAndWorkModule = module {
    single<ServerAddressProvider> {
        DefaultServerAddressProvider(
            preferenceService = get<PreferenceService>(),
        )
    }

    single<OkHttpClient> {
        get<OkHttpClient>(qualifier = Qualifiers.okHttpBase)
    }

    factory<RemoteSecretManager> {
        NoOpRemoteSecretManagerImpl()
    }
}

val onlyOnPremModule = emptyModule()
