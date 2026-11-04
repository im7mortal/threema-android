package ch.threema.app.activities.starred

import ch.threema.app.usecases.GetStarredMessagesUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val starredFeatureModule = module {
    viewModel<StarredMessagesViewModel>()
    factory<GetStarredMessagesUseCase>()
}
