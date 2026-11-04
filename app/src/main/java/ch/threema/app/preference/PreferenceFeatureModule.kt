package ch.threema.app.preference

import ch.threema.app.preference.usecases.RemoveAllPrivateMarksUseCase
import ch.threema.app.preference.usecases.WatchDebugLogSummaryUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val preferenceFeatureModule = module {
    factory<RemoveAllPrivateMarksUseCase>()
    factory<WatchDebugLogSummaryUseCase>()
}
