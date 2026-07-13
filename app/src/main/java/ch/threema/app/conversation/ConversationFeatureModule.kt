package ch.threema.app.conversation

import ch.threema.app.conversation.wallpaper.ConversationWallpaperViewModel
import ch.threema.app.usecases.contacts.WatchContactAvatarIterationUseCase
import ch.threema.app.usecases.conversation.MarkConversationAsReadUseCase
import ch.threema.app.usecases.conversation.ReportConversationShortcutUsedUseCase
import ch.threema.app.usecases.groups.WatchGroupAvatarIterationUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val conversationFeatureModule = module {
    viewModel { parameters ->
        ConversationViewModel(
            conversationId = parameters[0],
            hasInitialFocus = parameters[1],
            initialText = parameters[2],
            globalEventBuses = get(),
            conversationCategoryService = get(),
            preferenceService = get(),
            notificationService = get(),
            getAndPrepareAvatarUseCase = get(),
            markConversationAsReadUseCase = get(),
            watchConversationContactReceiverStateUseCase = get(),
            watchConversationGroupReceiverStateUseCase = get(),
            watchConversationDistributionListReceiverStateUseCase = get(),
            reportConversationShortcutUsedUseCase = get(),
        )
    }
    viewModelOf(::ConversationWallpaperViewModel)

    factoryOf(::MarkConversationAsReadUseCase)
    factoryOf(::ReportConversationShortcutUsedUseCase)

    factoryOf(::WatchConversationContactReceiverStateUseCase)
    factoryOf(::WatchConversationGroupReceiverStateUseCase)
    factoryOf(::WatchConversationDistributionListReceiverStateUseCase)

    factoryOf(::WatchContactAvatarIterationUseCase)
    factoryOf(::WatchGroupAvatarIterationUseCase)
}
