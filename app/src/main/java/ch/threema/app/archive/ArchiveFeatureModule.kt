package ch.threema.app.archive

import ch.threema.app.usecases.conversations.WatchArchivedConversationsUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val archiveFeatureModule = module {
    viewModel<ArchiveViewModel>()
    factory<WatchArchivedConversationsUseCase>()
}
