package ch.threema.app.di.modules

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import ch.threema.app.utils.AndroidContactUtil
import ch.threema.app.utils.AppRestarter
import ch.threema.app.utils.AppVersionProvider
import ch.threema.app.utils.DeviceIdProvider
import ch.threema.app.utils.HibernationUtil
import ch.threema.app.utils.LocaleProvider
import ch.threema.app.utils.QuoteUtil
import ch.threema.app.utils.SoundEffectPlayer
import ch.threema.app.utils.executor.CoroutineBackgroundExecutor
import ch.threema.common.DispatcherProvider
import ch.threema.common.TimeProvider
import ch.threema.common.UUIDGenerator
import ch.threema.common.secureRandom
import ch.threema.domain.models.AppVersion
import ch.threema.domain.protocol.Version
import java.security.SecureRandom
import kotlin.time.TimeSource
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

/**
 * Provides access to utility classes.
 * Note that some of these may be functionally singletons, but that should be treated as an implementation detail only, i.e., they should
 * not hold global state.
 */
val utilsModule = module {
    factory<AndroidContactUtil> { AndroidContactUtil.getInstance() }
    factory<DeviceIdProvider>()
    factory<LocaleProvider>()
    factory<SharedPreferences> { PreferenceManager.getDefaultSharedPreferences(get()) }
    factory<HibernationUtil>()
    factory<CoroutineBackgroundExecutor>()
    factory<WorkManager> { WorkManager.getInstance(get()) }
    factory<SoundEffectPlayer>()
    factory<AppVersion> { AppVersionProvider.appVersion } bind Version::class
    factory<AppRestarter>()
    factory<QuoteUtil>()

    // From 'common' Gradle module
    factory<DispatcherProvider> { DispatcherProvider.default }
    factory<TimeProvider> { TimeProvider.default }
    factory<SecureRandom> { secureRandom() }
    factory<UUIDGenerator> { UUIDGenerator.default }
    factory<TimeSource.WithComparableMarks> { TimeSource.Monotonic }
}
