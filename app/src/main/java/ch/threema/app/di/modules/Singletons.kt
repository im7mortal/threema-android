package ch.threema.app.di.modules

import ch.threema.app.GlobalAppState
import ch.threema.app.di.MasterKeyLockStateChangeHandler
import ch.threema.app.di.SessionScopeContainerHolder
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.GlobalEventBusesImpl
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.monitors.MonitorController
import ch.threema.app.monitors.MonitorProvider
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceServiceImpl
import ch.threema.app.services.ActivityService
import ch.threema.app.services.FileService
import ch.threema.app.services.FileServiceImpl
import ch.threema.app.services.LockAppAlarmScheduler
import ch.threema.app.services.LockAppService
import ch.threema.app.services.LockAppServiceImpl
import ch.threema.app.services.LockAppTimer
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.services.NotificationPreferenceServiceImpl
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.RingtoneServiceImpl
import ch.threema.app.services.avatarcache.AvatarCacheService
import ch.threema.app.services.avatarcache.AvatarCacheServiceImpl
import ch.threema.app.services.notification.BadgeUpdater
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.startup.AppStartupMonitorImpl
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.EncryptedPreferenceStoreImpl
import ch.threema.app.stores.IdentityProviderImpl
import ch.threema.app.stores.MutableIdentityProvider
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.stores.PreferenceStoreImpl
import ch.threema.app.utils.ConnectionIndicatorUtil
import ch.threema.app.utils.DeviceCookieManagerImpl
import ch.threema.app.utils.StateBitmapUtil
import ch.threema.app.webclient.services.SessionWakeUpService
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.data.IdentityProvider
import ch.threema.data.repositories.ServerMessageModelRepository
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.MasterKeyProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Provides access to all the singleton components, i.e., the ones that exist throughout the app's entire lifecycle.
 * These components may hold global state and as such it is important that they are treated as singletons.
 */
val singletonsModule = module {
    singleOf(::SessionScopeContainerHolder)
    singleOf(::AppStartupMonitorImpl) bind AppStartupMonitor::class

    singleOf(::MasterKeyLockStateChangeHandler)

    single<PreferenceStore> {
        PreferenceStoreImpl(
            sharedPreferences = get(),
            commit = false,
        )
    }
    single<EncryptedPreferenceStore> {
        EncryptedPreferenceStoreImpl(
            directory = get<AppDirectoryProvider>().appDataDirectory,
            masterKeyProvider = get(),
        )
    }
    singleOf(::ActivityService)
    singleOf(::AvatarCacheServiceImpl) bind AvatarCacheService::class
    singleOf(::FileServiceImpl) bind FileService::class
    singleOf(::IdentityProviderImpl) binds arrayOf(IdentityProvider::class, MutableIdentityProvider::class)
    single<LockAppService> {
        LockAppServiceImpl(
            lockAppTimer = LockAppTimer(
                preferencesService = get(),
                timeSource = get(),
                lockAppAlarmScheduler = LockAppAlarmScheduler(
                    appContext = get(),
                ),
            ),
            preferencesService = get(),
            identityProvider = get(),
        )
    }
    singleOf(::BadgeUpdater)
    singleOf(::NotificationPreferenceServiceImpl) bind NotificationPreferenceService::class
    singleOf(::PreferenceServiceImpl) bind PreferenceService::class
    singleOf(::RingtoneServiceImpl) bind RingtoneService::class
    singleOf(::SessionWakeUpServiceImpl) bind SessionWakeUpService::class
    singleOf(::SymmetricEncryptionService)
    singleOf(::DeviceCookieManagerImpl) bind DeviceCookieManager::class
    singleOf(::MonitorProvider)
    singleOf(::MonitorController)
    singleOf(::GlobalEventBusesImpl) binds arrayOf(GlobalEventBuses::class, GlobalEventFlows::class)

    singleOf(::ServerMessageModelRepository)

    factory<MasterKeyProvider> { get<MasterKeyManager>().masterKeyProvider }

    single { StateBitmapUtil(get()) }
    single { ConnectionIndicatorUtil(get()) }

    singleOf(::GlobalAppState)

    includes(okHttpClientsModule)
}
