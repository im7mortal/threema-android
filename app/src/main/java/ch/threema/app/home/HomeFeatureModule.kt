package ch.threema.app.home

import ch.threema.app.home.usecases.CheckServerMessagesUseCase
import ch.threema.app.home.usecases.GetStarredMessagesCountUseCase
import ch.threema.app.home.usecases.GetUnreadConversationCountUseCase
import ch.threema.app.home.usecases.SetUpThreemaChannelUseCase
import ch.threema.app.home.usecases.ShouldShowWorkIntroScreenUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val homeFeatureModule = module {
    viewModel<HomeViewModel>()
    factory<ErrorReportingDialog>()
    factory<GetUnreadConversationCountUseCase>()
    factory<GetStarredMessagesCountUseCase>()
    factory<CheckServerMessagesUseCase>()
    factory<SetUpThreemaChannelUseCase>()
    factory<ShouldShowWorkIntroScreenUseCase>()
}
