package ch.threema.app.errorreporting

import android.os.Build
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.logging.BaseErrorRecordStore
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val errorReportingModule = module {
    factory<ErrorReportingHelper>()
    factory<RecentErrorTypeIdStore>()
    factory<ErrorReportDetailsProvider>()
    factory<SendErrorReportWorker.Scheduler>()
    single<ErrorRecordStore> {
        ErrorRecordStoreImpl.create(
            context = get(),
            timeProvider = get(),
            uuidGenerator = get(),
        )
    } bind BaseErrorRecordStore::class
    factory<SentryService>()
    factory<SentryIdProvider>()
    factory {
        SentryService.Config(
            host = BuildConfig.SENTRY_HOST,
            projectId = BuildConfig.SENTRY_PROJECT_ID,
            publicApiKey = BuildConfig.SENTRY_PUBLIC_API_KEY,
        )
    }
    factory {
        SentryService.MetaInfo(
            androidSdkVersion = Build.VERSION.SDK_INT,
            release = "threema-android@${BuildConfig.BASE_VERSION_NAME}${BuildConfig.VERSION_NAME_BETA_SUFFIX}",
            versionCode = BuildConfig.DEFAULT_VERSION_CODE,
            buildFlavor = BuildFlavor.fullDisplayName,
            deviceModel = Build.MODEL,
        )
    }
}
