package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.conversion.toAssessment
import ch.threema.app.libthreema.conversion.toDisplayType
import ch.threema.app.libthreema.conversion.toGroupIdentity
import ch.threema.app.libthreema.conversion.toLibthreemaGroupIdentity
import ch.threema.app.libthreema.conversion.toLibthreemaPollAnnounceType
import ch.threema.app.libthreema.conversion.toLibthreemaPollAnswerType
import ch.threema.app.libthreema.conversion.toLibthreemaPollDisplayMode
import ch.threema.app.libthreema.conversion.toLibthreemaPollState
import ch.threema.app.libthreema.conversion.toPollState
import ch.threema.app.libthreema.conversion.toType
import ch.threema.app.libthreema.helper.BatchSizeProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.common.takeUnlessEmpty
import ch.threema.common.toEpochMilliInstant
import ch.threema.data.datatypes.ApiPollChoiceId
import ch.threema.data.datatypes.LocalPollChoiceId
import ch.threema.data.datatypes.localGroupId
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.ConversationId as LibthreemaConversationId
import ch.threema.libthreema.Poll as LibthreemaPoll
import ch.threema.libthreema.PollChoice as LibthreemaPollChoice
import ch.threema.libthreema.PollProvider as LibthreemaPollProvider
import ch.threema.libthreema.PollVote as LibthreemaPollVote
import ch.threema.libthreema.PollVotes as LibthreemaPollVotes
import ch.threema.libthreema.PollVotesSummary as LibthreemaPollVotesSummary
import ch.threema.libthreema.ProviderException as LibthreemaProviderException
import ch.threema.storage.factories.GroupPollModelFactory
import ch.threema.storage.factories.IdentityPollModelFactory
import ch.threema.storage.factories.PollChoiceModelFactory
import ch.threema.storage.factories.PollModelFactory
import ch.threema.storage.factories.PollVoteModelFactory
import ch.threema.storage.models.poll.GroupPollModel
import ch.threema.storage.models.poll.IdentityPollModel
import ch.threema.storage.models.poll.PollChoiceModel
import ch.threema.storage.models.poll.PollModel
import ch.threema.storage.models.poll.PollVoteModel
import java.time.Instant

private val logger = getThreemaLogger("PollProvider")

class PollProvider(
    private val pollModelFactory: PollModelFactory,
    private val identityPollModelFactory: IdentityPollModelFactory,
    private val groupPollModelFactory: GroupPollModelFactory,
    private val pollChoiceModelFactory: PollChoiceModelFactory,
    private val pollVoteModelFactory: PollVoteModelFactory,
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    override val guaranteedIdentityProvider: GuaranteedIdentityProvider,
    private val batchSizeProvider: BatchSizeProvider,
) : LibthreemaPollProvider, IdentityAwareProvider() {

    override fun getMany(batchIndex: ULong): List<LibthreemaPoll> =
        withTimeMeasurement(logger, "getMany") {
            val batchSize = batchSizeProvider.pollBatchSize
            val polls = pollModelFactory.getBatch(
                count = batchSize,
                offset = batchIndex.toInt() * batchSize,
            )
            val pollIds = polls.map(PollModel::id).toSet()
            val identitiesByPollIds = identityPollModelFactory.getIdentitiesByPollIds(pollIds)
            val groupIdentitiesByPollIds = groupPollModelFactory.getGroupIdentitiesByPollIds(pollIds)
            val choicesByPollIds = pollChoiceModelFactory.getByPollIds(pollIds)
            val votesByPollIds = pollVoteModelFactory.getByPollIds(pollIds)
            val userIdentity = guaranteedIdentityProvider.getIdentity()

            return polls.mapNotNull { poll ->
                val conversationId = identitiesByPollIds[poll.id]
                    ?.let { identity ->
                        LibthreemaConversationId.Contact(identity)
                    }
                    ?: groupIdentitiesByPollIds[poll.id]
                        ?.let { groupIdentity ->
                            LibthreemaConversationId.Group(groupIdentity.toLibthreemaGroupIdentity())
                        }
                    ?: run {
                        logger.warn("Skipping detached poll (poll id={}, api poll id={})", poll.id, poll.apiPollId)
                        return@mapNotNull null
                    }

                val displayType = poll.displayType ?: PollModel.DisplayType.SUMMARY_MODE
                val choices = choicesByPollIds[poll.id]
                    ?: emptyList()
                val apiPollChoiceIdByLocalPollChoiceId = choices.associate {
                    it.id to it.apiPollChoiceId
                }
                val votesByIdentity = votesByPollIds[poll.id]
                    ?.filter { it.votingIdentity != null }
                    ?.groupBy { it.votingIdentity!! }
                    ?: emptyMap()

                LibthreemaPoll(
                    id = poll.apiPollId.hexToULong(),
                    conversation = conversationId,
                    creatorIdentity = poll.creatorIdentity,
                    createdAt = poll.createdAt.toEpochMilli().toULong(),
                    state = poll.state.toLibthreemaPollState(),
                    description = poll.name ?: "",
                    answerType = poll.assessment.toLibthreemaPollAnswerType(),
                    announceType = poll.type.toLibthreemaPollAnnounceType(),
                    displayMode = displayType.toLibthreemaPollDisplayMode(),
                    choices = choices
                        .map { pollChoice ->
                            LibthreemaPollChoice(
                                id = pollChoice.apiPollChoiceId.toUInt(),
                                description = pollChoice.name ?: "",
                            )
                        },
                    votes = if (
                        poll.creatorIdentity == userIdentity.value ||
                        displayType == PollModel.DisplayType.LIST_MODE
                    ) {
                        LibthreemaPollVotes.Votes(
                            v1 = votesByIdentity.mapValues { (_, votes) ->
                                LibthreemaPollVote(
                                    choices = votes
                                        .filter(PollVoteModel::selected)
                                        .map { apiPollChoiceIdByLocalPollChoiceId[it.pollChoiceId]!!.toUInt() }
                                        .toSet(),
                                    createdAt = votes.maxOf { it.createdAt!! }.toEpochMilli().toULong(),
                                    modifiedAt = votes.maxOf { it.modifiedAt!! }.toEpochMilli().toULong(),
                                )
                            },
                        )
                    } else {
                        LibthreemaPollVotes.Summary(
                            v1 = LibthreemaPollVotesSummary(
                                summary = choices.associate { choice ->
                                    choice.apiPollChoiceId.toUInt() to choice.voteCount.toUShort()
                                },
                                userChoices = votesByIdentity[userIdentity.value]
                                    ?.filter(PollVoteModel::selected)
                                    ?.map { apiPollChoiceIdByLocalPollChoiceId[it.pollChoiceId]!!.toUInt() }
                                    ?.toSet()
                                    ?: emptySet(),
                            ),
                        )
                    },
                )
            }
        }

    override fun add(polls: List<LibthreemaPoll>) =
        withTimeMeasurement(logger, "add", polls.size) {
            checkPreconditions(polls)

            polls.forEach { poll ->
                val createdAt = poll.createdAt.toEpochMilliInstant()
                val pollModel = addPollModel(poll, createdAt)
                addLinkToConversation(poll, pollModel)
                val localPollChoiceIdByApiPollChoiceId = addChoices(poll, pollModel, createdAt)
                addVotes(poll, pollModel, getPollChoiceId = { apiChoiceId -> localPollChoiceIdByApiPollChoiceId[apiChoiceId]!! })
            }
        }

    private fun checkPreconditions(polls: List<LibthreemaPoll>) {
        checkPollsBelongToKnownConversations(polls)
        checkPollCreatorsAreKnown(polls)
        checkPollsDoNotExistYet(polls)
        checkPollVotersAreKnown(polls)
    }

    private fun checkPollsBelongToKnownConversations(polls: List<LibthreemaPoll>) {
        checkAllIdentitiesExist(
            identities = polls
                .mapNotNull { poll ->
                    (poll.conversation as? LibthreemaConversationId.Contact)?.v1
                },
        ) { expectedCount, actualCount ->
            "Can not create polls for unknown contacts (expected $expectedCount, found $actualCount)"
        }

        polls
            .mapNotNull { poll ->
                (poll.conversation as? LibthreemaConversationId.Group)?.v1?.toGroupIdentity()
            }
            .takeUnlessEmpty()
            ?.toSet()
            ?.let { groupIdentities ->
                if (!groupModelRepository.allExist(groupIdentities)) {
                    throw LibthreemaProviderException.ContractViolation("Can not create poll for unknown group conversation")
                }
            }

        if (polls.any { it.conversation is LibthreemaConversationId.DistributionList }) {
            throw LibthreemaProviderException.ContractViolation("Cannot create poll in a distribution list")
        }
    }

    private fun checkAllIdentitiesExist(identities: Collection<IdentityString>, error: (expectedCount: UInt, actualCount: UInt) -> String) {
        identities.takeUnlessEmpty()
            ?.toSet()
            ?.let { identities ->
                val expectedCount = identities.size.toUInt()
                val actualCount = contactModelRepository.countExisting(identities)
                if (actualCount != expectedCount) {
                    throw LibthreemaProviderException.ContractViolation(error(expectedCount, actualCount))
                }
            }
    }

    private fun checkPollCreatorsAreKnown(polls: List<LibthreemaPoll>) {
        val myIdentity = guaranteedIdentityProvider.getIdentityString()
        checkAllIdentitiesExist(
            identities = polls
                .mapNotNull { poll ->
                    poll.creatorIdentity.takeUnless { it == myIdentity }
                },
        ) { expectedCount, actualCount ->
            "Can not create poll from unknown creator (expected $expectedCount, found $actualCount)"
        }
    }

    private fun checkPollsDoNotExistYet(polls: List<LibthreemaPoll>) {
        polls.forEach { poll ->
            when (val conversationId = poll.conversation) {
                is LibthreemaConversationId.Contact -> {
                    if (identityPollModelFactory.exists(apiPollId = poll.id.toHexString(), identity = conversationId.v1)) {
                        throw LibthreemaProviderException.ContractViolation("Poll with same id already exists for contact")
                    }
                }

                is LibthreemaConversationId.Group -> {
                    val groupIdentity = conversationId.v1.toGroupIdentity()
                    if (groupPollModelFactory.exists(apiPollId = poll.id.toHexString(), groupIdentity = groupIdentity)) {
                        throw LibthreemaProviderException.ContractViolation("Poll with same id already exists for group conversation")
                    }
                }

                is LibthreemaConversationId.DistributionList -> Unit
            }
        }
    }

    private fun checkPollVotersAreKnown(polls: List<LibthreemaPoll>) {
        val myIdentity = guaranteedIdentityProvider.getIdentityString()
        checkAllIdentitiesExist(
            identities = polls.flatMap { poll ->
                (poll.votes as? LibthreemaPollVotes.Votes)?.v1?.keys
                    ?.filter { it != myIdentity }
                    ?: emptyList()
            },
        ) { expectedCount, actualCount ->
            "Can not create polls with unknown voters (expected $expectedCount, found $actualCount)"
        }
    }

    private fun addPollModel(poll: LibthreemaPoll, createdAt: Instant): PollModel {
        val pollModel = PollModel(
            apiPollId = poll.id.toHexString(),
            creatorIdentity = poll.creatorIdentity,
            name = poll.description,
            state = poll.state.toPollState(),
            assessment = poll.answerType.toAssessment(),
            type = poll.announceType.toType(),
            displayType = poll.displayMode.toDisplayType(),
            createdAt = createdAt,
            modifiedAt = createdAt,
            lastViewedAt = createdAt,
        )
        pollModelFactory.create(pollModel)
        return pollModel
    }

    private fun addLinkToConversation(poll: LibthreemaPoll, pollModel: PollModel) {
        when (val conversation = poll.conversation) {
            is LibthreemaConversationId.Contact -> {
                identityPollModelFactory.create(
                    IdentityPollModel(
                        identity = conversation.v1,
                        pollId = pollModel.id,
                    ),
                )
            }
            is LibthreemaConversationId.Group -> {
                groupPollModelFactory.create(
                    GroupPollModel(
                        groupId = groupModelRepository.getByGroupIdentity(conversation.v1.toGroupIdentity())!!.localGroupId.id,
                        pollId = pollModel.id,
                    ),
                )
            }
            is LibthreemaConversationId.DistributionList -> Unit
        }
    }

    private fun addChoices(poll: LibthreemaPoll, pollModel: PollModel, createdAt: Instant): Map<ApiPollChoiceId, LocalPollChoiceId> {
        val voteCountsByApiChoiceId: Map<ApiPollChoiceId, Int> = buildMap {
            when (val votes = poll.votes) {
                is LibthreemaPollVotes.Summary -> {
                    votes.v1.summary.forEach { (choiceIdU, count) ->
                        val apiChoiceId = choiceIdU.toInt()
                        put(apiChoiceId, (get(apiChoiceId) ?: 0) + count.toInt())
                    }
                }
                is LibthreemaPollVotes.Votes -> Unit
            }
        }
        return buildMap {
            poll.choices.forEachIndexed { index, choice ->
                val apiChoiceId: ApiPollChoiceId = choice.id.toInt()
                val pollChoiceModel = PollChoiceModel(
                    pollId = pollModel.id,
                    apiPollChoiceId = apiChoiceId,
                    name = choice.description,
                    voteCount = voteCountsByApiChoiceId[apiChoiceId] ?: 0,
                    order = index,
                    createdAt = createdAt,
                    modifiedAt = createdAt,
                )
                pollChoiceModelFactory.create(pollChoiceModel)
                put(apiChoiceId, pollChoiceModel.id)
            }
        }
    }

    private fun addVotes(poll: LibthreemaPoll, pollModel: PollModel, getPollChoiceId: (ApiPollChoiceId) -> LocalPollChoiceId) {
        when (val votes = poll.votes) {
            is LibthreemaPollVotes.Votes -> {
                votes.v1.forEach { (identity, identityVotes) ->
                    val voteCreatedAt = identityVotes.createdAt.toEpochMilliInstant()
                    val voteModifiedAt = identityVotes.modifiedAt?.toEpochMilliInstant()
                        ?: voteCreatedAt
                    identityVotes.choices.forEach { apiChoiceId ->
                        pollVoteModelFactory.create(
                            PollVoteModel(
                                pollId = pollModel.id,
                                pollChoiceId = getPollChoiceId(apiChoiceId.toInt()),
                                votingIdentity = identity,
                                selected = true,
                                createdAt = voteCreatedAt,
                                modifiedAt = voteModifiedAt,
                            ),
                        )
                    }
                }
            }
            is LibthreemaPollVotes.Summary -> Unit
        }
    }
}
