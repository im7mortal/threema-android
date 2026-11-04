package ch.threema.app.conversation

import ch.threema.app.conversation.wallpaper.ConversationWallpaperViewModel
import ch.threema.app.usecases.contacts.WatchContactAvatarIterationUseCase
import ch.threema.app.usecases.conversation.MarkConversationAsReadUseCase
import ch.threema.app.usecases.conversation.ReportConversationShortcutUsedUseCase
import ch.threema.app.usecases.groups.WatchGroupAvatarIterationUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val conversationFeatureModule = module {
    viewModel<ConversationViewModel>()
    viewModel<ConversationWallpaperViewModel>()

    factory<MarkConversationAsReadUseCase>()
    factory<ReportConversationShortcutUsedUseCase>()

    factory<WatchConversationContactReceiverStateUseCase>()
    factory<WatchConversationGroupReceiverStateUseCase>()
    factory<WatchConversationDistributionListReceiverStateUseCase>()

    factory<WatchContactAvatarIterationUseCase>()
    factory<WatchGroupAvatarIterationUseCase>()
}
