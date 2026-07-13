package ch.threema.storage

import ch.threema.data.IdentityProvider
import ch.threema.storage.factories.ContactModelFactory
import ch.threema.storage.factories.DistributionListMemberModelFactory
import ch.threema.storage.factories.DistributionListMessageModelFactory
import ch.threema.storage.factories.DistributionListModelFactory
import ch.threema.storage.factories.GroupCallModelFactory
import ch.threema.storage.factories.GroupMemberModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.factories.GroupModelFactory
import ch.threema.storage.factories.GroupPollModelFactory
import ch.threema.storage.factories.IdentityPollModelFactory
import ch.threema.storage.factories.IncomingGroupSyncRequestLogModelFactory
import ch.threema.storage.factories.MessageModelFactory
import ch.threema.storage.factories.OutgoingGroupSyncRequestLogModelFactory
import ch.threema.storage.factories.PollChoiceModelFactory
import ch.threema.storage.factories.PollModelFactory
import ch.threema.storage.factories.PollVoteModelFactory
import ch.threema.storage.factories.RejectedGroupMessageFactory

@Deprecated("Use Koin to inject model factories directly")
class DatabaseService(
    private val databaseProvider: DatabaseProvider,
    private val identityProvider: IdentityProvider,
) {
    val contactModelFactory: ContactModelFactory by lazy {
        ContactModelFactory(databaseProvider, identityProvider)
    }
    val messageModelFactory: MessageModelFactory by lazy {
        MessageModelFactory(databaseProvider)
    }
    val groupModelFactory: GroupModelFactory by lazy {
        GroupModelFactory(databaseProvider)
    }
    val groupMemberModelFactory: GroupMemberModelFactory by lazy {
        GroupMemberModelFactory(databaseProvider)
    }
    val groupMessageModelFactory: GroupMessageModelFactory by lazy {
        GroupMessageModelFactory(databaseProvider)
    }
    val distributionListModelFactory: DistributionListModelFactory by lazy {
        DistributionListModelFactory(databaseProvider)
    }
    val distributionListMemberModelFactory: DistributionListMemberModelFactory by lazy {
        DistributionListMemberModelFactory(databaseProvider)
    }
    val distributionListMessageModelFactory: DistributionListMessageModelFactory by lazy {
        DistributionListMessageModelFactory(databaseProvider)
    }
    val outgoingGroupSyncRequestLogModelFactory: OutgoingGroupSyncRequestLogModelFactory by lazy {
        OutgoingGroupSyncRequestLogModelFactory(databaseProvider)
    }
    val incomingGroupSyncRequestLogModelFactory: IncomingGroupSyncRequestLogModelFactory by lazy {
        IncomingGroupSyncRequestLogModelFactory(databaseProvider)
    }
    val pollModelFactory: PollModelFactory by lazy {
        PollModelFactory(databaseProvider)
    }
    val pollChoiceModelFactory: PollChoiceModelFactory by lazy {
        PollChoiceModelFactory(databaseProvider)
    }
    val pollVoteModelFactory: PollVoteModelFactory by lazy {
        PollVoteModelFactory(databaseProvider)
    }
    val identityPollModelFactory: IdentityPollModelFactory by lazy {
        IdentityPollModelFactory(databaseProvider)
    }
    val groupPollModelFactory: GroupPollModelFactory by lazy {
        GroupPollModelFactory(databaseProvider)
    }
    val groupCallModelFactory: GroupCallModelFactory by lazy {
        GroupCallModelFactory(databaseProvider)
    }
    val rejectedGroupMessageFactory: RejectedGroupMessageFactory by lazy {
        RejectedGroupMessageFactory(databaseProvider)
    }
}
