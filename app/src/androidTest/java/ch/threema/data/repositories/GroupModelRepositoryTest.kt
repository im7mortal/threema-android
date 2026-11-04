package ch.threema.data.repositories

import app.cash.turbine.test
import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestNonceStore
import ch.threema.app.TestTaskManager
import ch.threema.app.eventbus.GlobalEventBusesImpl
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.base.crypto.NonceFactory
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.GroupModelData
import ch.threema.data.models.GroupModelDataFactory
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbGroup
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.UserState
import ch.threema.domain.types.IdentityString
import ch.threema.test.TestData
import ch.threema.test.TestData.GroupIdentities
import ch.threema.test.TestDatabaseProvider
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.TestDispatcherProvider
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.testDispatcherProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GroupModelRepositoryTest {

    private fun createModelRepository(
        dispatcherProvider: DispatcherProvider = TestDispatcherProvider(),
    ): Triple<GroupModelRepository, DatabaseBackend, GlobalEventFlows> {
        val testIdentityProvider = TestIdentityProvider(
            identity = TestData.Identities.ME,
        )
        val databaseProvider = TestDatabaseProvider()
        val databaseBackend = SqliteDatabaseBackend(
            databaseProvider = databaseProvider,
            identityProvider = testIdentityProvider,
        )
        val globalEventFlows = GlobalEventBusesImpl(dispatcherProvider)
        val groupModelRepository = ModelRepositories(
            databaseProvider = databaseProvider,
            identityProvider = testIdentityProvider,
            multiDeviceManager = TestMultiDeviceManager(),
            taskManager = TestTaskManager(UnusedTaskCodec()),
            nonceFactory = NonceFactory(TestNonceStore()),
            globalEventBuses = globalEventFlows,
            globalEventFlows = globalEventFlows,
            dispatcherProvider = dispatcherProvider,
            timeProvider = TestTimeProvider(),
        ).groups
        return Triple(
            groupModelRepository,
            databaseBackend,
            globalEventFlows,
        )
    }

    @Test
    fun getByGroupIdentityNotFound() {
        val (groupModelRepository) = createModelRepository()
        assertNull(
            groupModelRepository.getByGroupIdentity(GroupIdentities.GROUP_1),
        )
    }

    @Test
    fun getByCreatorIdentityAndIdNotFound() {
        val (groupModelRepository) = createModelRepository()
        assertNull(
            groupModelRepository.getByCreatorIdentityAndId(
                creatorIdentity = GroupIdentities.GROUP_1.creatorIdentity,
                groupId = GroupId(GroupIdentities.GROUP_1.groupId),
            ),
        )
    }

    @Test
    fun getByGroupIdentityExisting() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // Create group using the database backend directly
        databaseBackend.createGroup(
            group = createDbGroup(
                groupIdentity = GroupIdentities.GROUP_1,
            ),
        )

        // Fetch group using the group model repository
        val model = groupModelRepository.getByGroupIdentity(GroupIdentities.GROUP_1)!!
        assertEquals(
            expected = GroupIdentities.GROUP_1,
            actual = model.groupIdentity,
        )
    }

    @Test
    fun getByCreatorIdentityAndIdExisting() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        val groupIdentity = GroupIdentities.GROUP_1

        // Create group using the database backend directly
        databaseBackend.createGroup(
            group = createDbGroup(groupIdentity),
        )

        // Fetch group using the "new" model
        val model = groupModelRepository.getByCreatorIdentityAndId(
            creatorIdentity = groupIdentity.creatorIdentity,
            groupId = GroupId(groupIdentity.groupId),
        )
        assertEquals(
            expected = groupIdentity,
            actual = model?.groupIdentity,
        )
    }

    @Test
    fun getByGroupDatabaseId() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // arrange
        val testDbGroup = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_1,
        )
        databaseBackend.createGroup(testDbGroup)

        // act
        // The database is initially empty and the local group id starts with 1
        val fetchedGroup = groupModelRepository.getByGroupDatabaseId(1)

        // assert
        assertEquals(
            expected = GroupModelDataFactory.toDataType(testDbGroup),
            actual = fetchedGroup?.data,
        )
    }

    @Test
    fun getByCreatorIdentityAndId() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // arrange
        val testDbGroup = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_1,
        )
        databaseBackend.createGroup(testDbGroup)

        // act
        val fetchedGroup = groupModelRepository.getByCreatorIdentityAndId(
            GroupIdentities.GROUP_1.creatorIdentity,
            GroupId(GroupIdentities.GROUP_1.groupId),
        )

        // assert
        assertEquals(
            expected = GroupModelDataFactory.toDataType(testDbGroup),
            actual = fetchedGroup?.data,
        )
    }

    @Test
    fun testGetByCreatorIdentity() {
        val (groupModelRepository, databaseBackend) = createModelRepository()

        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val testGroup = createDbGroup(groupIdentity)
        databaseBackend.createGroup(testGroup)

        val fetchedGroups = groupModelRepository.getByCreator(
            groupIdentity.creatorIdentity,
        )
        assertEquals(listOf(GroupModelDataFactory.toDataType(testGroup)), fetchedGroups.map { it.data })
    }

    @Test
    fun getByGroupIdentity() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        val defaultGroup = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_1,
            otherMembers = setOf(
                TestData.Identities.OTHER_2.value,
                TestData.Identities.OTHER_3.value,
            ),
        )
        testInsertAndGet(
            groupModelRepository = groupModelRepository,
            databaseBackend = databaseBackend,
            groupIdentity = GroupIdentities.GROUP_1,
            testGroup = defaultGroup,
        )

        val emptyGroup = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_2,
            otherMembers = emptySet(),
        )
        testInsertAndGet(
            groupModelRepository = groupModelRepository,
            databaseBackend = databaseBackend,
            groupIdentity = GroupIdentities.GROUP_2,
            testGroup = emptyGroup,
        )
    }

    @Test
    fun testMemberSetModification() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        val defaultGroup = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_1,
        )
        testInsertAndGet(
            groupModelRepository = groupModelRepository,
            databaseBackend = databaseBackend,
            groupIdentity = GroupIdentities.GROUP_1,
            testGroup = defaultGroup,
        )

        val testData = groupModelRepository.getByGroupIdentity(GroupIdentities.GROUP_1)!!.data!!
        assertFailsWith<UnsupportedOperationException> {
            // Casting the set to a mutable set will work, but adding a new member to the set should
            // result in a runtime exception. Note that this is mainly in java code a problem, as
            // there is no cast needed to add a new member. Of course, it will result in a runtime
            // exception as well.
            (testData.otherMembers as MutableSet).add("01234567")
        }
    }

    private fun testInsertAndGet(
        groupModelRepository: GroupModelRepository,
        databaseBackend: DatabaseBackend,
        groupIdentity: GroupIdentity,
        testGroup: DbGroup,
    ) {
        databaseBackend.createGroup(testGroup)
        val fetchedGroup = groupModelRepository.getByGroupIdentity(groupIdentity)
        assertEquals(
            expected = GroupModelDataFactory.toDataType(testGroup),
            actual = fetchedGroup?.data,
        )
    }

    @Test
    fun isPartOfAnyGroup_ReturnsTrueForContactAsCreator() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = setOf(
                    TestData.Identities.OTHER_2.value,
                ),
                userState = UserState.MEMBER,
            ),
        )

        // act
        val isPart = groupModelRepository.isPartOfAnyGroup(
            contactIdentity = TestData.Identities.OTHER_1.value,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroup_ReturnsTrueForContactAsMember() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = setOf(
                    TestData.Identities.OTHER_2.value,
                    TestData.Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = groupModelRepository.isPartOfAnyGroup(
            contactIdentity = TestData.Identities.OTHER_2.value,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroup_ReturnsFalseIfContactNotCreatorNorMember() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = setOf(
                    TestData.Identities.OTHER_2.value,
                    TestData.Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = groupModelRepository.isPartOfAnyGroup(
            contactIdentity = TestData.Identities.OTHER_4.value,
        )

        // assert
        assertFalse(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_ReturnsTrueForContactAsCreator() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = setOf(
                    TestData.Identities.OTHER_2.value,
                ),
                userState = UserState.MEMBER,
            ),
        )

        // act
        val isPart = groupModelRepository.isPartOfAnyGroupWithUserState(
            contactIdentity = TestData.Identities.OTHER_1.value,
            userState = UserState.MEMBER,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_ReturnsTrueForContactAsMember() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = setOf(
                    TestData.Identities.OTHER_2.value,
                    TestData.Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = groupModelRepository.isPartOfAnyGroupWithUserState(
            contactIdentity = TestData.Identities.OTHER_2.value,
            userState = UserState.LEFT,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_ReturnsFalseIfContactNotCreatorNorMember() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = setOf(
                    TestData.Identities.OTHER_2.value,
                    TestData.Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = groupModelRepository.isPartOfAnyGroupWithUserState(
            contactIdentity = TestData.Identities.OTHER_4.value,
            userState = UserState.LEFT,
        )

        // assert
        assertFalse(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_RespectsUserStateFilterMember() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = setOf(
                    TestData.Identities.OTHER_2.value,
                    TestData.Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = groupModelRepository.isPartOfAnyGroupWithUserState(
            contactIdentity = TestData.Identities.OTHER_1.value,
            userState = UserState.MEMBER,
        )

        // assert
        assertFalse(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_RespectsUserStateFilterKicked() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = setOf(
                    TestData.Identities.OTHER_2.value,
                    TestData.Identities.OTHER_3.value,
                ),
                userState = UserState.KICKED,
            ),
        )

        // act
        val isPart = groupModelRepository.isPartOfAnyGroupWithUserState(
            contactIdentity = TestData.Identities.OTHER_1.value,
            userState = UserState.MEMBER,
        )

        // assert
        assertFalse(isPart)
    }

    @Test
    fun existsAnyByGroupIdentity_returns_true_for_single_match_cached() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_2,
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        val groupIdentitiesToCheck = setOf(
            GroupIdentities.GROUP_1,
        )

        // act
        val exists = groupModelRepository.existsAnyByGroupIdentity(groupIdentitiesToCheck)

        // assert
        assertTrue(exists)
    }

    @Test
    fun existsAnyByGroupIdentity_returns_true_for_multiple_match_cached() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_2,
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        val groupIdentitiesToCheck = setOf(
            GroupIdentities.GROUP_2,
            GroupIdentities.GROUP_1,
        )

        // act
        val exists = groupModelRepository.existsAnyByGroupIdentity(groupIdentitiesToCheck)

        // assert
        assertTrue(exists)
    }

    @Test
    fun existsAnyByGroupIdentity_returns_false_for_no_match() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_2,
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        val groupIdentitiesToCheck = setOf(
            GroupIdentities.GROUP_3,
            GroupIdentities.GROUP_4,
        )

        // act
        val exists = groupModelRepository.existsAnyByGroupIdentity(groupIdentitiesToCheck)

        // assert
        assertFalse(exists)
    }

    @Test
    fun existsAnyByGroupIdentity_returns_false_for_empty_input() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_2,
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )

        // act
        val exists = groupModelRepository.existsAnyByGroupIdentity(
            groupIdentities = emptySet(),
        )

        // assert
        assertFalse(exists)
    }

    @Test
    fun getAllByGroupIdentities_returns_empty_for_empty_input() {
        val (groupModelRepository) = createModelRepository()

        // act
        val groupModels = groupModelRepository.getAllByGroupIdentities(
            groupIdentities = emptySet(),
        )

        // assert
        assertEquals(
            expected = 0,
            actual = groupModels.size,
        )
    }

    @Test
    fun getAllByGroupIdentities_finds_single_group_cached() {
        val (groupModelRepository) = createModelRepository()

        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_2,
            ),
        )

        // act
        val groupModels = groupModelRepository.getAllByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_2,
            ),
        )

        // assert
        assertEquals(
            expected = 1,
            actual = groupModels.size,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_2,
            ),
            actual = groupModels.first().data,
        )
    }

    @Test
    fun getAllByGroupIdentities_finds_multiple_groups_cached() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_2,
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_3,
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_4,
            ),
        )

        // act
        val groupModels = groupModelRepository.getAllByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_4,
                GroupIdentities.GROUP_2,
            ),
        )

        // assert
        assertEquals(
            expected = 2,
            actual = groupModels.size,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_4,
            ),
            actual = groupModels[0].data,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_2,
            ),
            actual = groupModels[1].data,
        )
    }

    @Test
    fun getAllByGroupIdentities_finds_some_groups() {
        val (groupModelRepository) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_3,
            ),
        )

        // act
        val groupModels = groupModelRepository.getAllByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
                GroupIdentities.GROUP_3,
            ),
        )

        // assert
        assertEquals(
            expected = 2,
            actual = groupModels.size,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
            ),
            actual = groupModels[0].data,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_3,
            ),
            actual = groupModels[1].data,
        )
    }

    /**
     * "mixed" meaning that some models can be found in cache, others in db
     */
    @Test
    fun getAllByGroupIdentities_finds_all_groups_mixed() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // arrange
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
            ),
        )
        // bypasses the model cache for this one group
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentities.GROUP_2,
                otherMembers = emptySet(),
            ),
        )
        groupModelRepository.persistNewGroup(
            groupModelData = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_3,
            ),
        )

        // act
        val groupModels = groupModelRepository.getAllByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
                GroupIdentities.GROUP_3,
            ),
        )

        // assert
        assertEquals(
            expected = 3,
            actual = groupModels.size,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_1,
            ),
            actual = groupModels[0].data,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_2,
            ),
            actual = groupModels[1].data,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_3,
            ),
            actual = groupModels[2].data,
        )
    }

    @Test
    fun persistNewGroups_returns_empty_for_empty_input() {
        val (groupModelRepository) = createModelRepository()
        // act
        val persistedGroups = groupModelRepository.persistNewGroups(
            groups = emptySet(),
        )

        // assert
        assertEquals(
            expected = 0,
            actual = persistedGroups.size,
        )
        val actualGroups = groupModelRepository.getAll()
        assertEquals(
            expected = 0,
            actual = actualGroups.size,
        )
    }

    @Test
    fun persistNewGroups_fails_when_group_already_exists() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentities.GROUP_2,
            ),
        )

        // act / assert
        assertFailsWith<GroupAlreadyExistsException> {
            groupModelRepository.persistNewGroups(
                groups = setOf(
                    createGroupModelData(
                        groupIdentity = GroupIdentities.GROUP_2,
                    ),
                    createGroupModelData(
                        groupIdentity = GroupIdentities.GROUP_3,
                    ),
                ),
            )
        }
        val actualGroups = groupModelRepository.getAll()
        assertEquals(
            expected = 1,
            actual = actualGroups.size,
        )
        assertEquals(
            expected = GroupIdentities.GROUP_2,
            actual = actualGroups.first().groupIdentity,
        )
    }

    @Test
    fun persistNewGroups_persists_single_group() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentities.GROUP_1,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentities.GROUP_2,
            ),
        )

        // act
        val persistedGroups = groupModelRepository.persistNewGroups(
            groups = setOf(
                createGroupModelData(
                    groupIdentity = GroupIdentities.GROUP_3,
                ),
            ),
        )

        // assert
        assertEquals(
            expected = 1,
            actual = persistedGroups.size,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_3,
            ),
            actual = persistedGroups.first().data,
        )
        val actualGroups = groupModelRepository.getAll()
        assertEquals(
            expected = 3,
            actual = actualGroups.size,
        )
        assertTrue(
            actualGroups.any { it.data?.groupIdentity == GroupIdentities.GROUP_3 },
        )
    }

    @Test
    fun persistNewGroups_persists_multiple_groups() {
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentities.GROUP_1,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentities.GROUP_2,
            ),
        )

        // act
        val persistedGroups = groupModelRepository.persistNewGroups(
            groups = setOf(
                createGroupModelData(
                    groupIdentity = GroupIdentities.GROUP_3,
                ),
                createGroupModelData(
                    groupIdentity = GroupIdentities.GROUP_4,
                ),
            ),
        )

        // assert
        assertEquals(
            expected = 2,
            actual = persistedGroups.size,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_3,
            ),
            actual = persistedGroups[0].data,
        )
        assertEquals(
            expected = createGroupModelData(
                groupIdentity = GroupIdentities.GROUP_4,
            ),
            actual = persistedGroups[1].data,
        )
        val actualGroups = groupModelRepository.getAll()
        assertEquals(
            expected = 4,
            actual = actualGroups.size,
        )
        assertTrue(
            actualGroups.any { it.data?.groupIdentity == GroupIdentities.GROUP_3 },
        )
        assertTrue(
            actualGroups.any { it.data?.groupIdentity == GroupIdentities.GROUP_4 },
        )
    }

    @Test
    fun persistNewGroups_emits_events() = runTest {
        val (groupModelRepository, _, globalEventFlows) = createModelRepository(
            dispatcherProvider = testDispatcherProvider(),
        )

        // act / assert
        globalEventFlows.groups.test {
            groupModelRepository.persistNewGroups(
                groups = setOf(
                    createGroupModelData(
                        groupIdentity = GroupIdentities.GROUP_1,
                        otherMembers = setOf(
                            TestData.Identities.OTHER_2.value,
                            TestData.Identities.OTHER_3.value,
                        ),
                        userState = UserState.MEMBER,
                    ),
                    createGroupModelData(
                        groupIdentity = GroupIdentities.GROUP_ME,
                        otherMembers = setOf(
                            TestData.Identities.OTHER_1.value,
                        ),
                        userState = UserState.MEMBER,
                    ),
                    createGroupModelData(
                        groupIdentity = GroupIdentities.GROUP_2,
                        otherMembers = setOf(
                            TestData.Identities.OTHER_3.value,
                        ),
                        userState = UserState.LEFT,
                    ),
                ),
            )
            expectItem(GroupEvent.NewGroup(GroupIdentities.GROUP_1))
            expectItem(GroupEvent.NewMember(GroupIdentities.GROUP_1, TestData.Identities.ME))
            expectItem(GroupEvent.NewMember(GroupIdentities.GROUP_1, TestData.Identities.OTHER_1))
            expectItem(GroupEvent.NewMember(GroupIdentities.GROUP_1, TestData.Identities.OTHER_2))
            expectItem(GroupEvent.NewMember(GroupIdentities.GROUP_1, TestData.Identities.OTHER_3))

            expectItem(GroupEvent.NewGroup(GroupIdentities.GROUP_ME))
            expectItem(GroupEvent.NewMember(GroupIdentities.GROUP_ME, TestData.Identities.ME))
            expectItem(GroupEvent.NewMember(GroupIdentities.GROUP_ME, TestData.Identities.OTHER_1))

            expectItem(GroupEvent.NewGroup(GroupIdentities.GROUP_2))
            expectItem(GroupEvent.NewMember(GroupIdentities.GROUP_2, TestData.Identities.OTHER_2))
            expectItem(GroupEvent.NewMember(GroupIdentities.GROUP_2, TestData.Identities.OTHER_3))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun allExist_empty_input() {
        // arrange
        val (groupModelRepository) = createModelRepository()

        // act
        val allExist = groupModelRepository.allExist(
            groupIdentities = emptySet(),
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allExist_empty_cache_and_db() {
        // arrange
        val (groupModelRepository) = createModelRepository()

        // act
        val allExist = groupModelRepository.allExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
            ),
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allExist_none() {
        // arrange
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // Group 1 and 2 are in model cache, group 3 is not
        groupModelRepository.persistNewGroups(
            setOf(
                createGroupModelData(GroupIdentities.GROUP_1),
                createGroupModelData(GroupIdentities.GROUP_2),
            ),
        )
        databaseBackend.createGroups(
            setOf(
                createDbGroup(GroupIdentities.GROUP_3),
            ),
        )

        // act
        val allExist = groupModelRepository.allExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_ME,
                GroupIdentities.GROUP_4,
            ),
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allExist_single_cache() {
        // arrange
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // Group 1 and 2 are in model cache, group 3 is not
        groupModelRepository.persistNewGroups(
            setOf(
                createGroupModelData(GroupIdentities.GROUP_1),
                createGroupModelData(GroupIdentities.GROUP_2),
            ),
        )
        databaseBackend.createGroups(
            setOf(
                createDbGroup(GroupIdentities.GROUP_3),
            ),
        )

        // act
        val allExist = groupModelRepository.allExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_2,
            ),
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allExist_single_database() {
        // arrange
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // Group 1 and 2 are in model cache, group 3 is not
        groupModelRepository.persistNewGroups(
            setOf(
                createGroupModelData(GroupIdentities.GROUP_1),
                createGroupModelData(GroupIdentities.GROUP_2),
            ),
        )
        databaseBackend.createGroups(
            setOf(
                createDbGroup(GroupIdentities.GROUP_3),
            ),
        )

        // act
        val allExist = groupModelRepository.allExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_3,
            ),
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allExist_multiple_cache() {
        // arrange
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // Group 1 and 2 are in model cache, group 3 is not
        groupModelRepository.persistNewGroups(
            setOf(
                createGroupModelData(GroupIdentities.GROUP_1),
                createGroupModelData(GroupIdentities.GROUP_2),
            ),
        )
        databaseBackend.createGroups(
            setOf(
                createDbGroup(GroupIdentities.GROUP_3),
            ),
        )

        // act
        val allExist = groupModelRepository.allExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
            ),
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allExist_multiple_database() {
        // arrange
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // Group 1 and 2 are in model cache, group 3 and 4 are not
        groupModelRepository.persistNewGroups(
            setOf(
                createGroupModelData(GroupIdentities.GROUP_1),
                createGroupModelData(GroupIdentities.GROUP_2),
            ),
        )
        databaseBackend.createGroups(
            setOf(
                createDbGroup(GroupIdentities.GROUP_3),
                createDbGroup(GroupIdentities.GROUP_4),
            ),
        )

        // act
        val allExist = groupModelRepository.allExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_3,
                GroupIdentities.GROUP_4,
            ),
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allExist_multiple_cache_and_database_mix() {
        // arrange
        val (groupModelRepository, databaseBackend) = createModelRepository()
        // Group 1 and 2 are in model cache, group 3 and 4 are not
        groupModelRepository.persistNewGroups(
            setOf(
                createGroupModelData(GroupIdentities.GROUP_1),
                createGroupModelData(GroupIdentities.GROUP_2),
            ),
        )
        databaseBackend.createGroups(
            setOf(
                createDbGroup(GroupIdentities.GROUP_3),
                createDbGroup(GroupIdentities.GROUP_4),
            ),
        )

        // act
        val allExist = groupModelRepository.allExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_4,
            ),
        )

        // assert
        assertTrue(allExist)
    }

    private companion object {

        private fun createDbGroup(
            groupIdentity: GroupIdentity,
            otherMembers: Set<IdentityString> = emptySet(),
            userState: UserState = UserState.MEMBER,
        ) = DbGroup(
            creatorIdentity = groupIdentity.creatorIdentity,
            groupId = groupIdentity.apiGroupIdHexString,
            name = "Group",
            createdAt = utcDate(2026, 7, 21),
            synchronizedAt = null,
            lastUpdate = null,
            conversationVisibility = ConversationVisibility.NORMAL,
            conversationCategory = ConversationCategory.DEFAULT.serializedValue,
            colorIndex = 0,
            groupDescription = null,
            groupDescriptionChangedAt = null,
            members = otherMembers,
            userState = userState,
            notificationTriggerPolicyOverridePolicy = null,
            notificationTriggerPolicyOverrideExpiresAt = null,
            spamReportingState = ConversationSpamReportingState.DEFAULT.serializedValue,
        )

        private fun createGroupModelData(
            groupIdentity: GroupIdentity,
            otherMembers: Set<IdentityString> = emptySet(),
            userState: UserState = UserState.MEMBER,
        ) = GroupModelData(
            groupIdentity = groupIdentity,
            name = "Group",
            createdAt = utcDate(2026, 7, 21),
            synchronizedAt = null,
            lastUpdate = null,
            conversationVisibility = ConversationVisibility.NORMAL,
            conversationCategory = ConversationCategory.DEFAULT,
            precomputedIdColor = IdColor(0),
            groupDescription = null,
            groupDescriptionChangedAt = null,
            otherMembers = otherMembers,
            userState = userState,
            notificationTriggerPolicyOverride = null,
            spamReportingState = ConversationSpamReportingState.DEFAULT,
        )
    }
}
