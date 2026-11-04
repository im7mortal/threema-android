package ch.threema.storage

import android.content.Context
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.storage.factories.AppTaskPersistenceFactory
import ch.threema.storage.factories.ContactEditHistoryEntryModelFactory
import ch.threema.storage.factories.ContactEmojiReactionModelFactory
import ch.threema.storage.factories.ContactMessageModelFactory
import ch.threema.storage.factories.ContactModelFactory
import ch.threema.storage.factories.ConversationTagFactory
import ch.threema.storage.factories.DistributionListMemberModelFactory
import ch.threema.storage.factories.DistributionListMessageModelFactory
import ch.threema.storage.factories.DistributionListModelFactory
import ch.threema.storage.factories.GroupCallModelFactory
import ch.threema.storage.factories.GroupEditHistoryEntryModelFactory
import ch.threema.storage.factories.GroupEmojiReactionModelFactory
import ch.threema.storage.factories.GroupMemberModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.factories.GroupModelFactory
import ch.threema.storage.factories.GroupPollModelFactory
import ch.threema.storage.factories.IdentityPollModelFactory
import ch.threema.storage.factories.IncomingGroupSyncRequestLogModelFactory
import ch.threema.storage.factories.OutgoingGroupSyncRequestLogModelFactory
import ch.threema.storage.factories.PollChoiceModelFactory
import ch.threema.storage.factories.PollModelFactory
import ch.threema.storage.factories.PollVoteModelFactory
import ch.threema.storage.factories.RejectedGroupMessageFactory
import ch.threema.storage.factories.ServerMessageModelFactory
import ch.threema.storage.factories.TaskArchiveFactory
import ch.threema.storage.factories.WebClientSessionModelFactory
import kotlin.system.exitProcess
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val storageModule = module {
    factory<DatabaseDowngradeHelperImpl>() bind DatabaseDowngradeHelper::class
    single {
        DatabaseProviderImpl(
            databaseOpenHelperFactory = { masterKey ->
                val appContext = get<Context>()
                DatabaseOpenHelper(
                    appContext = appContext,
                    password = masterKey.deriveDatabasePassword(),
                    onDatabaseCorrupted = {
                        appContext.showToast("Database corrupted. Please restart your device and try again.", ToastDuration.LONG)
                        exitProcess(2)
                    },
                    downgradeHelper = get(),
                )
            },
        )
    } bind DatabaseProvider::class

    single<AppTaskPersistenceFactory>()
    single<ContactEditHistoryEntryModelFactory>()
    single<ConversationTagFactory> { ConversationTagFactory(get()) }
    single<GroupEditHistoryEntryModelFactory>()
    single<ContactEmojiReactionModelFactory>()
    single<GroupEmojiReactionModelFactory>()
    single<ServerMessageModelFactory>()
    single<TaskArchiveFactory>()
    single<WebClientSessionModelFactory> { WebClientSessionModelFactory(get()) }

    single<DatabaseService>()
    factory<ContactModelFactory> { get<DatabaseService>().contactModelFactory }
    factory<ContactMessageModelFactory> { get<DatabaseService>().contactMessageModelFactory }
    factory<GroupModelFactory> { get<DatabaseService>().groupModelFactory }
    factory<GroupMemberModelFactory> { get<DatabaseService>().groupMemberModelFactory }
    factory<GroupMessageModelFactory> { get<DatabaseService>().groupMessageModelFactory }
    factory<DistributionListModelFactory> { get<DatabaseService>().distributionListModelFactory }
    factory<DistributionListMemberModelFactory> { get<DatabaseService>().distributionListMemberModelFactory }
    factory<DistributionListMessageModelFactory> { get<DatabaseService>().distributionListMessageModelFactory }
    factory<OutgoingGroupSyncRequestLogModelFactory> { get<DatabaseService>().outgoingGroupSyncRequestLogModelFactory }
    factory<IncomingGroupSyncRequestLogModelFactory> { get<DatabaseService>().incomingGroupSyncRequestLogModelFactory }
    factory<PollModelFactory> { get<DatabaseService>().pollModelFactory }
    factory<PollChoiceModelFactory> { get<DatabaseService>().pollChoiceModelFactory }
    factory<PollVoteModelFactory> { get<DatabaseService>().pollVoteModelFactory }
    factory<IdentityPollModelFactory> { get<DatabaseService>().identityPollModelFactory }
    factory<GroupPollModelFactory> { get<DatabaseService>().groupPollModelFactory }
    factory<GroupCallModelFactory> { get<DatabaseService>().groupCallModelFactory }
    factory<RejectedGroupMessageFactory> { get<DatabaseService>().rejectedGroupMessageFactory }
}
