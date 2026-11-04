package ch.threema.app.problemsolving

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val problemSolvingFeatureModule = module {
    factory<GetProblemsUseCase>()
}
