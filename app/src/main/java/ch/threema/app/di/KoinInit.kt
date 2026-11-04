package ch.threema.app.di

import android.content.Context
import ch.threema.app.di.modules.appTypeSpecificModule
import ch.threema.app.di.modules.featuresModule
import ch.threema.app.di.modules.sessionScopedModule
import ch.threema.app.di.modules.singletonsModule
import ch.threema.app.di.modules.utilsModule
import ch.threema.app.libthreema.conversion.libthreemaConversionModule
import ch.threema.app.libthreema.helper.libthreemaHelperModule
import ch.threema.app.libthreema.provider.libthreemaProviderModule
import ch.threema.app.protocolsteps.protocolStepsModule
import ch.threema.app.usecases.useCasesModule
import ch.threema.storage.storageModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.plugin.module.dsl.startKoin

@KoinApplication
@ComponentScan
private class ThreemaKoinApp

fun initDependencyInjection(appContext: Context) {
    startKoin<ThreemaKoinApp> {
        logger(ThreemaKoinLogger)
        androidContext(appContext)

        modules(
            utilsModule,
            singletonsModule,
            storageModule,
            sessionScopedModule,
            useCasesModule,
            featuresModule,
            appTypeSpecificModule,
            libthreemaConversionModule,
            libthreemaHelperModule,
            libthreemaProviderModule,
            protocolStepsModule,
        )
    }
}
