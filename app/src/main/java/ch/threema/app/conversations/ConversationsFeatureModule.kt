package ch.threema.app.conversations

import ch.threema.app.conversation.ConversationRefreshMonitor
import ch.threema.app.conversation.GroupStatusMessageMonitor
import ch.threema.app.conversation.MessageViewElementFactory
import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.conversation.ExportConversationToFileUseCase
import ch.threema.app.usecases.conversations.CountArchivedConversationsUseCase
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase
import ch.threema.app.usecases.conversations.WatchUnarchivedConversationListItemsUseCase
import ch.threema.app.usecases.conversations.WatchUnarchivedConversationsUseCase
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val conversationsFeatureModule = module {
    viewModel<ConversationsViewModel>()

    factory<EmptyOrDeleteConversationsUseCase>()
    factory<ExportConversationToFileUseCase>()
    factory<WatchUnarchivedConversationsUseCase>()
    factory<WatchUnarchivedConversationListItemsUseCase>()
    factory<WatchContactNameFormatSettingUseCase>()
    factory<WatchAllMentionNamesUseCase>()
    factory<CountArchivedConversationsUseCase>()

    single<MessageViewElementFactory>()
    single<ConversationRefreshMonitor>()
    single<GroupStatusMessageMonitor>()
}
