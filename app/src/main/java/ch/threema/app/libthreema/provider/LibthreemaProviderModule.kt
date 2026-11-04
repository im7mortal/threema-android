package ch.threema.app.libthreema.provider

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val libthreemaProviderModule = module {
    factory<ContactProvider>()
    factory<ConversationProvider>()
    factory<CspNonceProvider>()
    factory<D2dNonceProvider>()
    factory<DistributionListProvider>()
    factory<GroupProvider>()
    factory<GuaranteedIdentityProvider>()
    factory<PollProvider>()
    factory<ProvidersProvider>()
    factory<SettingsProvider>()
    factory<UserProfileProvider>()
}
