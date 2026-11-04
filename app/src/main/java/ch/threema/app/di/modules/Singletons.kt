package ch.threema.app.di.modules

import ch.threema.app.GlobalAppState
import ch.threema.app.connection.LoggedInConnectionRequester
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
import ch.threema.app.services.notification.PassphraseNotificationService
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
import ch.threema.app.wallpapers.WallpaperService
import ch.threema.app.wallpapers.WallpaperServiceImpl
import ch.threema.app.webclient.services.SessionWakeUpService
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.data.IdentityProvider
import ch.threema.data.repositories.ServerMessageModelRepository
import ch.threema.domain.protocol.ChallengeResponseHelper
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.MasterKeyProvider
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Provides access to all the singleton components, i.e., the ones that exist throughout the app's entire lifecycle.
 * These components may hold global state and as such it is important that they are treated as singletons.
 */
val singletonsModule = module {
    single<SessionScopeContainerHolder>()
    single<AppStartupMonitorImpl>() bind AppStartupMonitor::class

    single<MasterKeyLockStateChangeHandler>()

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
    single<ActivityService>()
    single { AvatarCacheServiceImpl(get()) } bind AvatarCacheService::class
    single<FileServiceImpl>() bind FileService::class
    single<PassphraseNotificationService>()
    single<IdentityProviderImpl>() bind IdentityProvider::class
    factory<MutableIdentityProvider> { get<IdentityProviderImpl>() }
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
    single { BadgeUpdater(get()) }
    single<NotificationPreferenceServiceImpl>() bind NotificationPreferenceService::class
    single<PreferenceServiceImpl>() bind PreferenceService::class
    single { RingtoneServiceImpl(get()) } bind RingtoneService::class
    single { SessionWakeUpServiceImpl(get()) } bind SessionWakeUpService::class
    single<SymmetricEncryptionService>()
    single<WallpaperServiceImpl>() bind WallpaperService::class
    single<DeviceCookieManagerImpl>() bind DeviceCookieManager::class
    single<MonitorProvider>()
    single<MonitorController>()
    single<GlobalEventBusesImpl>() bind GlobalEventBuses::class
    factory<GlobalEventFlows> { get<GlobalEventBusesImpl>() }

    single<ServerMessageModelRepository>()

    factory<MasterKeyProvider> { get<MasterKeyManager>().masterKeyProvider }

    single { StateBitmapUtil(get()) }
    single { ConnectionIndicatorUtil(get()) }

    single<GlobalAppState>()
    single<LoggedInConnectionRequester>()
    single<ChallengeResponseHelper>()

    includes(okHttpClientsModule)
}
