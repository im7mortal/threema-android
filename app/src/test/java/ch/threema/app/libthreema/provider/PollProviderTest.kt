package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.helper.BatchSizeProvider
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.ConversationId as LibthreemaConversationId
import ch.threema.libthreema.GroupIdentity as LibthreemaGroupIdentity
import ch.threema.libthreema.Poll as LibthreemaPoll
import ch.threema.libthreema.PollAnnounceType as LibthreemaPollAnnounceType
import ch.threema.libthreema.PollAnswerType as LibthreemaPollAnswerType
import ch.threema.libthreema.PollChoice as LibthreemaPollChoice
import ch.threema.libthreema.PollDisplayMode as LibthreemaPollDisplayMode
import ch.threema.libthreema.PollState as LibthreemaPollState
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
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.utcDate
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import testdata.TestData

class PollProviderTest {

    private lateinit var pollModelFactoryMock: PollModelFactory
    private lateinit var identityPollModelFactoryMock: IdentityPollModelFactory
    private lateinit var groupPollModelFactoryMock: GroupPollModelFactory
    private lateinit var pollChoiceModelFactoryMock: PollChoiceModelFactory
    private lateinit var pollVoteModelFactoryMock: PollVoteModelFactory
    private lateinit var contactModelRepositoryMock: ContactModelRepository
    private lateinit var groupModelRepositoryMock: GroupModelRepository
    private val myIdentity = TestData.Identities.ME
    private val guaranteedIdentityProvider = GuaranteedIdentityProvider(
        TestIdentityProvider(myIdentity),
    )
    private val batchSize = 10
    private lateinit var pollProvider: PollProvider

    @BeforeTest
    fun setUp() {
        pollModelFactoryMock = mockk()
        identityPollModelFactoryMock = mockk()
        groupPollModelFactoryMock = mockk()
        pollChoiceModelFactoryMock = mockk()
        pollVoteModelFactoryMock = mockk()
        contactModelRepositoryMock = mockk()
        groupModelRepositoryMock = mockk()
        pollProvider = PollProvider(
            pollModelFactory = pollModelFactoryMock,
            identityPollModelFactory = identityPollModelFactoryMock,
            groupPollModelFactory = groupPollModelFactoryMock,
            pollChoiceModelFactory = pollChoiceModelFactoryMock,
            pollVoteModelFactory = pollVoteModelFactoryMock,
            contactModelRepository = contactModelRepositoryMock,
            groupModelRepository = groupModelRepositoryMock,
            guaranteedIdentityProvider = guaranteedIdentityProvider,
            batchSizeProvider = BatchSizeProvider(
                pollBatchSize = batchSize,
            ),
        )
    }

    @Test
    fun `get many`() {
        every { pollModelFactoryMock.getBatch(batchSize, batchSize * 20) } returns listOf(
            PollModel(
                id = 1,
                apiPollId = "ff000000000000aa",
                creatorIdentity = TestData.Identities.OTHER_1.value,
                name = "Poll 1",
                state = PollModel.State.OPEN,
                assessment = PollModel.Assessment.SINGLE_CHOICE,
                type = PollModel.Type.INTERMEDIATE,
                choiceType = PollModel.ChoiceType.TEXT,
                displayType = PollModel.DisplayType.LIST_MODE,
                createdAt = utcDate(2026, 1, 1),
                modifiedAt = utcDate(2026, 1, 2),
                lastViewedAt = null,
            ),
            PollModel(
                id = 2,
                apiPollId = "aa000000000000ff",
                creatorIdentity = TestData.Identities.OTHER_1.value,
                name = "Poll 2",
                state = PollModel.State.CLOSED,
                assessment = PollModel.Assessment.MULTIPLE_CHOICE,
                type = PollModel.Type.RESULT_ON_CLOSE,
                choiceType = PollModel.ChoiceType.TEXT,
                displayType = PollModel.DisplayType.SUMMARY_MODE,
                createdAt = utcDate(2026, 1, 1),
                modifiedAt = utcDate(2026, 1, 2),
                lastViewedAt = null,
            ),
        )
        every { identityPollModelFactoryMock.getIdentitiesByPollIds(setOf(1, 2)) } returns mapOf(
            2 to TestData.Identities.OTHER_1.value,
        )
        every { groupPollModelFactoryMock.getGroupIdentitiesByPollIds(setOf(1, 2)) } returns mapOf(
            1 to GroupIdentity(
                creatorIdentity = TestData.Identities.OTHER_2.value,
                groupId = 1000L,
            ),
        )
        every { pollChoiceModelFactoryMock.getByPollIds(setOf(1, 2)) } returns mapOf(
            1 to listOf(
                PollChoiceModel(
                    id = 9991,
                    pollId = 1,
                    apiPollChoiceId = 11,
                    name = "Choice 1",
                    voteCount = 1,
                ),
                PollChoiceModel(
                    id = 9992,
                    pollId = 1,
                    apiPollChoiceId = 12,
                    name = "Choice 2",
                    voteCount = 2,
                ),
            ),
            2 to listOf(
                PollChoiceModel(
                    id = 9993,
                    pollId = 2,
                    apiPollChoiceId = 21,
                    name = "Choice a",
                    voteCount = 1,
                ),
                PollChoiceModel(
                    id = 9994,
                    pollId = 2,
                    apiPollChoiceId = 22,
                    name = "Choice b",
                    voteCount = 1,
                ),
            ),
        )
        every { pollVoteModelFactoryMock.getByPollIds(setOf(1, 2)) } returns mapOf(
            1 to listOf(
                PollVoteModel(
                    pollChoiceId = 9991,
                    votingIdentity = TestData.Identities.OTHER_1.value,
                    selected = true,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 2),
                ),
                PollVoteModel(
                    pollChoiceId = 9992,
                    votingIdentity = TestData.Identities.OTHER_1.value,
                    selected = false,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 2),
                ),
                PollVoteModel(
                    pollChoiceId = 9992,
                    votingIdentity = TestData.Identities.OTHER_2.value,
                    selected = true,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 2),
                ),
            ),
            2 to listOf(
                PollVoteModel(
                    pollChoiceId = 9993,
                    votingIdentity = TestData.Identities.ME.value,
                    selected = true,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 2),
                ),
                PollVoteModel(
                    pollChoiceId = 9994,
                    votingIdentity = TestData.Identities.OTHER_1.value,
                    selected = false,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 2),
                ),
                PollVoteModel(
                    pollChoiceId = 9994,
                    votingIdentity = TestData.Identities.OTHER_1.value,
                    selected = true,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 2),
                ),
            ),
        )

        val result = pollProvider.getMany(batchIndex = 20.toULong())

        assertEquals(
            listOf(
                LibthreemaPoll(
                    id = 0xff000000000000aaUL,
                    conversation = LibthreemaConversationId.Group(
                        LibthreemaGroupIdentity(
                            groupId = 1000UL,
                            creatorIdentity = TestData.Identities.OTHER_2.value,
                        ),
                    ),
                    creatorIdentity = TestData.Identities.OTHER_1.value,
                    createdAt = utcDate(2026, 1, 1).toEpochMilli().toULong(),
                    state = LibthreemaPollState.OPEN,
                    description = "Poll 1",
                    answerType = LibthreemaPollAnswerType.SINGLE_CHOICE,
                    announceType = LibthreemaPollAnnounceType.EVERYONE,
                    displayMode = LibthreemaPollDisplayMode.LIST,
                    choices = listOf(
                        LibthreemaPollChoice(
                            id = 11U,
                            description = "Choice 1",
                        ),
                        LibthreemaPollChoice(
                            id = 12U,
                            description = "Choice 2",
                        ),
                    ),
                    votes = LibthreemaPollVotes.Votes(
                        v1 = mapOf(
                            TestData.Identities.OTHER_1.value to LibthreemaPollVote(
                                choices = setOf(11U),
                                createdAt = utcDate(2026, 1, 1).toEpochMilli().toULong(),
                                modifiedAt = utcDate(2026, 1, 2).toEpochMilli().toULong(),
                            ),
                            TestData.Identities.OTHER_2.value to LibthreemaPollVote(
                                choices = setOf(12U),
                                createdAt = utcDate(2026, 1, 1).toEpochMilli().toULong(),
                                modifiedAt = utcDate(2026, 1, 2).toEpochMilli().toULong(),
                            ),
                        ),
                    ),
                ),
                LibthreemaPoll(
                    id = 0xaa000000000000ffUL,
                    conversation = LibthreemaConversationId.Contact(TestData.Identities.OTHER_1.value),
                    creatorIdentity = TestData.Identities.OTHER_1.value,
                    createdAt = utcDate(2026, 1, 1).toEpochMilli().toULong(),
                    state = LibthreemaPollState.CLOSED,
                    description = "Poll 2",
                    answerType = LibthreemaPollAnswerType.MULTIPLE_CHOICE,
                    announceType = LibthreemaPollAnnounceType.CREATOR_ONLY,
                    displayMode = LibthreemaPollDisplayMode.SUMMARY,
                    choices = listOf(
                        LibthreemaPollChoice(
                            id = 21U,
                            description = "Choice a",
                        ),
                        LibthreemaPollChoice(
                            id = 22U,
                            description = "Choice b",
                        ),
                    ),
                    votes = LibthreemaPollVotes.Summary(
                        v1 = LibthreemaPollVotesSummary(
                            summary = mapOf(
                                21U to 1U,
                                22U to 1U,
                            ),
                            userChoices = setOf(
                                21U,
                            ),
                        ),
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun `cannot add poll to contact conversation of unknown contact`() {
        every { contactModelRepositoryMock.countExisting(any()) } returns 0u
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            pollProvider.add(
                listOf(
                    mockk {
                        every { conversation } returns LibthreemaConversationId.Contact(TestData.Identities.OTHER_1.value)
                    },
                ),
            )
        }
    }

    @Test
    fun `cannot add poll to group conversation that does not exist`() {
        val groupIdentity = GroupIdentity(creatorIdentity = TestData.Identities.OTHER_1.value, groupId = 123L)
        every { groupModelRepositoryMock.allExist(setOf(groupIdentity)) } returns false
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            pollProvider.add(
                listOf(
                    mockk {
                        every { conversation } returns LibthreemaConversationId.Group(
                            LibthreemaGroupIdentity(
                                creatorIdentity = TestData.Identities.OTHER_1.value,
                                groupId = 123UL,
                            ),
                        )
                    },
                ),
            )
        }
    }

    @Test
    fun `cannot add poll to distribution list`() {
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            pollProvider.add(
                listOf(
                    mockk {
                        every { conversation } returns LibthreemaConversationId.DistributionList(123UL)
                    },
                ),
            )
        }
    }

    @Test
    fun `cannot add poll from unknown creator`() {
        every { contactModelRepositoryMock.countExisting(any()) } answers {
            firstArg<Set<IdentityString>>().count { it == TestData.Identities.OTHER_1.value }.toUInt()
        }
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            pollProvider.add(
                listOf(
                    mockk {
                        every { conversation } returns LibthreemaConversationId.Contact(TestData.Identities.OTHER_1.value)
                        every { creatorIdentity } returns TestData.Identities.OTHER_2.value
                    },
                ),
            )
        }
    }

    @Test
    fun `cannot add poll with same id in same contact conversation`() {
        every { contactModelRepositoryMock.countExisting(setOf(TestData.Identities.OTHER_1.value)) } returns 1u
        every { identityPollModelFactoryMock.exists(apiPollId = 123UL.toHexString(), identity = TestData.Identities.OTHER_1.value) } returns true
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            pollProvider.add(
                listOf(
                    mockk {
                        every { conversation } returns LibthreemaConversationId.Contact(TestData.Identities.OTHER_1.value)
                        every { creatorIdentity } returns TestData.Identities.OTHER_1.value
                        every { id } returns 123UL
                    },
                ),
            )
        }
    }

    @Test
    fun `cannot add poll with same id in same group conversation`() {
        val groupIdentity = GroupIdentity(creatorIdentity = TestData.Identities.OTHER_1.value, groupId = 123L)
        every { contactModelRepositoryMock.countExisting(setOf(TestData.Identities.OTHER_1.value)) } returns 1u
        every { groupModelRepositoryMock.allExist(setOf(groupIdentity)) } returns true
        every { groupPollModelFactoryMock.exists(apiPollId = 123UL.toHexString(), groupIdentity = groupIdentity) } returns true
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            pollProvider.add(
                listOf(
                    mockk {
                        every { conversation } returns LibthreemaConversationId.Group(
                            LibthreemaGroupIdentity(
                                creatorIdentity = TestData.Identities.OTHER_1.value,
                                groupId = 123UL,
                            ),
                        )
                        every { creatorIdentity } returns TestData.Identities.OTHER_1.value
                        every { id } returns 123UL
                    },
                ),
            )
        }
    }

    @Test
    fun `cannot add poll with unknown voters`() {
        every { contactModelRepositoryMock.countExisting(any()) } answers {
            firstArg<Set<IdentityString>>().count { it == TestData.Identities.OTHER_1.value }.toUInt()
        }
        every { contactModelRepositoryMock.existsByIdentity(TestData.Identities.OTHER_1.value) } returns true
        every { groupModelRepositoryMock.allExist(any()) } returns true
        every { groupPollModelFactoryMock.exists(apiPollId = any(), groupIdentity = any()) } returns false
        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            pollProvider.add(
                listOf(
                    mockk {
                        every { conversation } returns LibthreemaConversationId.Group(
                            LibthreemaGroupIdentity(
                                creatorIdentity = TestData.Identities.OTHER_1.value,
                                groupId = 123UL,
                            ),
                        )
                        every { creatorIdentity } returns TestData.Identities.OTHER_1.value
                        every { id } returns 123UL
                        every { votes } returns LibthreemaPollVotes.Votes(
                            mapOf(
                                TestData.Identities.OTHER_2.value to mockk<LibthreemaPollVote>(),
                            ),
                        )
                    },
                ),
            )
        }
    }

    @Test
    fun `add closed poll with visible votes to contact conversation`() {
        val pollId = 99
        every { contactModelRepositoryMock.countExisting(setOf(TestData.Identities.OTHER_1.value)) } returns 1u
        every { identityPollModelFactoryMock.exists(any(), any()) } returns false
        every { pollModelFactoryMock.create(any()) } answers {
            firstArg<PollModel>().id = pollId
            true
        }
        every { pollChoiceModelFactoryMock.create(any()) } answers {
            firstArg<PollChoiceModel>().id = firstArg<PollChoiceModel>().apiPollChoiceId + 3000
            true
        }
        every { pollVoteModelFactoryMock.create(any()) } returns true
        every { identityPollModelFactoryMock.create(any()) } returns true

        pollProvider.add(
            listOf(
                LibthreemaPoll(
                    id = 100u,
                    conversation = LibthreemaConversationId.Contact(TestData.Identities.OTHER_1.value),
                    creatorIdentity = TestData.Identities.OTHER_1.value,
                    createdAt = utcDate(2026, 1, 1).toEpochMilli().toULong(),
                    state = LibthreemaPollState.CLOSED,
                    description = "Poll",
                    answerType = LibthreemaPollAnswerType.SINGLE_CHOICE,
                    announceType = LibthreemaPollAnnounceType.EVERYONE,
                    displayMode = LibthreemaPollDisplayMode.LIST,
                    choices = listOf(
                        LibthreemaPollChoice(
                            id = 91u,
                            description = "Choice 1",
                        ),
                        LibthreemaPollChoice(
                            id = 92u,
                            description = "Choice 2",
                        ),
                    ),
                    votes = LibthreemaPollVotes.Votes(
                        mapOf(
                            TestData.Identities.OTHER_1.value to LibthreemaPollVote(
                                choices = setOf(92u),
                                createdAt = utcDate(2026, 1, 2).toEpochMilli().toULong(),
                                modifiedAt = utcDate(2026, 1, 3).toEpochMilli().toULong(),
                            ),
                            TestData.Identities.ME.value to LibthreemaPollVote(
                                choices = setOf(92u),
                                createdAt = utcDate(2026, 1, 2).toEpochMilli().toULong(),
                                modifiedAt = utcDate(2026, 1, 3).toEpochMilli().toULong(),
                            ),
                        ),
                    ),
                ),
            ),
        )

        verify(exactly = 1) {
            pollModelFactoryMock.create(
                PollModel(
                    // the id is actually expected to be 0, but because PollModel is mutable by design, we have to do a workaround here
                    // and explicitly set the id for the purpose of the equality check in Mockk
                    id = pollId,
                    apiPollId = 100UL.toHexString(),
                    creatorIdentity = TestData.Identities.OTHER_1.value,
                    name = "Poll",
                    state = PollModel.State.CLOSED,
                    assessment = PollModel.Assessment.SINGLE_CHOICE,
                    type = PollModel.Type.INTERMEDIATE,
                    displayType = PollModel.DisplayType.LIST_MODE,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 1),
                    lastViewedAt = utcDate(2026, 1, 1),
                ),
            )
        }
        verify(exactly = 1) {
            identityPollModelFactoryMock.create(
                IdentityPollModel(
                    identity = TestData.Identities.OTHER_1.value,
                    pollId = pollId,
                ),
            )
        }
        verify(exactly = 1) {
            pollChoiceModelFactoryMock.create(
                PollChoiceModel(
                    // the id is actually expected to be 0, but because PollChoiceModel is mutable by design, we have to do a workaround here
                    // and explicitly set the id for the purpose of the equality check in Mockk
                    id = 3091,
                    pollId = pollId,
                    apiPollChoiceId = 91,
                    name = "Choice 1",
                    order = 0,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 1),
                ),
            )
        }
        verify(exactly = 1) {
            pollChoiceModelFactoryMock.create(
                PollChoiceModel(
                    // the id is actually expected to be 0, but because PollChoiceModel is mutable by design, we have to do a workaround here
                    // and explicitly set the id for the purpose of the equality check in Mockk
                    id = 3092,
                    pollId = pollId,
                    apiPollChoiceId = 92,
                    name = "Choice 2",
                    order = 1,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 1),
                ),
            )
        }
        verify(exactly = 1) {
            pollVoteModelFactoryMock.create(
                PollVoteModel(
                    pollId = pollId,
                    pollChoiceId = 3092,
                    votingIdentity = TestData.Identities.OTHER_1.value,
                    selected = true,
                    createdAt = utcDate(2026, 1, 2),
                    modifiedAt = utcDate(2026, 1, 3),
                ),
            )
        }
        verify(exactly = 1) {
            pollVoteModelFactoryMock.create(
                PollVoteModel(
                    pollId = pollId,
                    pollChoiceId = 3092,
                    votingIdentity = TestData.Identities.ME.value,
                    selected = true,
                    createdAt = utcDate(2026, 1, 2),
                    modifiedAt = utcDate(2026, 1, 3),
                ),
            )
        }
    }

    @Test
    fun `add open poll with hidden votes to group conversation`() {
        val groupIdentity = GroupIdentity(
            creatorIdentity = TestData.Identities.OTHER_1.value,
            groupId = 123,
        )
        val pollId = 99
        val localGroupId = 777L
        every { contactModelRepositoryMock.countExisting(setOf(TestData.Identities.OTHER_1.value)) } returns 1u
        every { groupPollModelFactoryMock.exists(any(), any()) } returns false
        every { groupModelRepositoryMock.allExist(any()) } returns true
        every { groupModelRepositoryMock.getByGroupIdentity(groupIdentity) } returns mockk {
            every { getDatabaseId() } returns localGroupId
        }
        every { pollModelFactoryMock.create(any()) } answers {
            firstArg<PollModel>().id = pollId
            true
        }
        every { pollChoiceModelFactoryMock.create(any()) } answers {
            firstArg<PollChoiceModel>().id = firstArg<PollChoiceModel>().apiPollChoiceId + 3000
            true
        }
        every { groupPollModelFactoryMock.create(any()) } returns true

        pollProvider.add(
            listOf(
                LibthreemaPoll(
                    id = 100u,
                    conversation = LibthreemaConversationId.Group(
                        LibthreemaGroupIdentity(
                            creatorIdentity = TestData.Identities.OTHER_1.value,
                            groupId = 123UL,
                        ),
                    ),
                    creatorIdentity = TestData.Identities.OTHER_1.value,
                    createdAt = utcDate(2026, 1, 1).toEpochMilli().toULong(),
                    state = LibthreemaPollState.OPEN,
                    description = "Poll",
                    answerType = LibthreemaPollAnswerType.MULTIPLE_CHOICE,
                    announceType = LibthreemaPollAnnounceType.CREATOR_ONLY,
                    displayMode = LibthreemaPollDisplayMode.SUMMARY,
                    choices = listOf(
                        LibthreemaPollChoice(
                            id = 91u,
                            description = "Choice 1",
                        ),
                        LibthreemaPollChoice(
                            id = 92u,
                            description = "Choice 2",
                        ),
                    ),
                    votes = LibthreemaPollVotes.Summary(
                        v1 = LibthreemaPollVotesSummary(
                            summary = mapOf(
                                91u to 2u,
                                92u to 1u,
                            ),
                            userChoices = setOf(),
                        ),
                    ),
                ),
            ),
        )

        verify(exactly = 1) {
            pollModelFactoryMock.create(
                PollModel(
                    // the id is actually expected to be 0, but because PollModel is mutable by design, we have to do a workaround here
                    // and explicitly set the id for the purpose of the equality check in Mockk
                    id = pollId,
                    apiPollId = 100UL.toHexString(),
                    creatorIdentity = TestData.Identities.OTHER_1.value,
                    name = "Poll",
                    state = PollModel.State.OPEN,
                    assessment = PollModel.Assessment.MULTIPLE_CHOICE,
                    type = PollModel.Type.RESULT_ON_CLOSE,
                    displayType = PollModel.DisplayType.SUMMARY_MODE,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 1),
                    lastViewedAt = utcDate(2026, 1, 1),
                ),
            )
        }
        verify(exactly = 1) {
            groupPollModelFactoryMock.create(
                GroupPollModel(
                    groupId = localGroupId.toInt(),
                    pollId = pollId,
                ),
            )
        }

        verify(exactly = 1) {
            pollChoiceModelFactoryMock.create(
                PollChoiceModel(
                    // the id is actually expected to be 0, but because PollChoiceModel is mutable by design, we have to do a workaround here
                    // and explicitly set the id for the purpose of the equality check in Mockk
                    id = 3091,
                    pollId = pollId,
                    apiPollChoiceId = 91,
                    name = "Choice 1",
                    voteCount = 2,
                    order = 0,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 1),
                ),
            )
        }
        verify(exactly = 1) {
            pollChoiceModelFactoryMock.create(
                PollChoiceModel(
                    // the id is actually expected to be 0, but because PollChoiceModel is mutable by design, we have to do a workaround here
                    // and explicitly set the id for the purpose of the equality check in Mockk
                    id = 3092,
                    pollId = pollId,
                    apiPollChoiceId = 92,
                    name = "Choice 2",
                    voteCount = 1,
                    order = 1,
                    createdAt = utcDate(2026, 1, 1),
                    modifiedAt = utcDate(2026, 1, 1),
                ),
            )
        }
    }
}
