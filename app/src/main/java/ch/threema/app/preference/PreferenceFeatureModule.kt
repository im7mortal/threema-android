package ch.threema.app.preference

import ch.threema.app.preference.usecases.RemoveAllPrivateMarksUseCase
import ch.threema.app.preference.usecases.WatchDebugLogSummaryUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val preferenceFeatureModule = module {
    factoryOf(::RemoveAllPrivateMarksUseCase)
    factoryOf(::WatchDebugLogSummaryUseCase)
}
