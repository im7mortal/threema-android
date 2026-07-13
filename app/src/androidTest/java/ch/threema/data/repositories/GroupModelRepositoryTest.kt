package ch.threema.data.repositories

import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestNonceStore
import ch.threema.app.TestTaskManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.GroupModelDataFactory
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbGroup
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.UserState
import ch.threema.storage.factories.GroupModelFactory
import ch.threema.storage.models.group.GroupModelOld
import ch.threema.test.TestDatabaseProvider
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher

class GroupModelRepositoryTest {
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var groupModelFactory: GroupModelFactory
    private lateinit var databaseBackend: DatabaseBackend
    private lateinit var multiDeviceManager: TestMultiDeviceManager
    private lateinit var taskManager: TestTaskManager
    private lateinit var nonceFactory: NonceFactory
    private lateinit var groupModelRepository: GroupModelRepository

    private fun createTestDbGroup(groupIdentity: GroupIdentity): DbGroup {
        return DbGroup(
            creatorIdentity = groupIdentity.creatorIdentity,
            groupId = groupIdentity.groupIdHexString,
            name = "Group",
            createdAt = Instant.now(),
            synchronizedAt = Instant.now(),
            lastUpdate = null,
            conversationVisibility = ConversationVisibility.NORMAL,
            colorIndex = 0,
            groupDescription = "Description",
            groupDescriptionChangedAt = Instant.now(),
            members = setOf("AAAAAAAA", "BBBBBBBB"),
            userState = UserState.MEMBER,
            notificationTriggerPolicyOverridePolicy = null,
            notificationTriggerPolicyOverrideExpiresAt = null,
        )
    }

    @BeforeTest
    fun before() {
        this.databaseProvider = TestDatabaseProvider()
        groupModelFactory = GroupModelFactory(databaseProvider)
        this.databaseBackend = SqliteDatabaseBackend(databaseProvider, mockk())
        multiDeviceManager = TestMultiDeviceManager()
        taskManager = TestTaskManager(UnusedTaskCodec())
        nonceFactory = NonceFactory(TestNonceStore())
        val testDispatcher = StandardTestDispatcher()
        this.groupModelRepository = ModelRepositories(
            databaseProvider = databaseProvider,
            identityProvider = mockk(),
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
            nonceFactory = nonceFactory,
            globalEventBuses = mockk(relaxed = true),
            globalEventFlows = mockk(relaxed = true),
            dispatcherProvider = mockk {
                every { worker } returns testDispatcher
            },
        ).groups
    }

    @Test
    fun getByGroupIdentityNotFound() {
        val groupIdentity = GroupIdentity("AAAAAAAA", 42)
        val model = groupModelRepository.getByGroupIdentity(groupIdentity)
        assertNull(model)
    }

    @Test
    fun getByCreatorIdentityAndIdNotFound() {
        val model = groupModelRepository.getByCreatorIdentityAndId("AAAAAAAA", GroupId(42))
        assertNull(model)
    }

    @Test
    fun getByGroupIdentityExisting() {
        val groupIdentity = GroupIdentity("TESTTEST", 42)

        // Create group using the "old" model
        groupModelFactory.create(
            GroupModelOld()
                .setCreatorIdentity(groupIdentity.creatorIdentity)
                .setApiGroupId(GroupId(groupIdentity.groupId))
                .setCreatedAt(Instant.now()),
        )

        // Fetch group using the "new" model
        val model = groupModelRepository.getByGroupIdentity(groupIdentity)!!
        assertEquals(groupIdentity, model.groupIdentity)
    }

    @Test
    fun getByCreatorIdentityAndIdExisting() {
        val creatorIdentity = "TESTTEST"
        val groupId = GroupId(-42)

        // Create group using the "old" model
        groupModelFactory.create(
            GroupModelOld()
                .setCreatorIdentity(creatorIdentity)
                .setApiGroupId(groupId)
                .setCreatedAt(Instant.now()),
        )

        // Fetch group using the "new" model
        val model = groupModelRepository.getByCreatorIdentityAndId(creatorIdentity, groupId)!!
        val groupIdentity = GroupIdentity(creatorIdentity, groupId.toLong())
        assertEquals(groupIdentity, model.groupIdentity)
    }

    @Test
    fun testGetByLocalId() {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val testGroup = createTestDbGroup(groupIdentity)
        databaseBackend.createGroup(testGroup)

        // This should work because the database is initially empty and the local group id starts
        // with 1.
        val fetchedGroup = groupModelRepository.getByGroupDatabaseId(1)
        assertEquals(GroupModelDataFactory.toDataType(testGroup), fetchedGroup?.data)
    }

    @Test
    fun testGetByCreatorIdentityAndGroupId() {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val testGroup = createTestDbGroup(groupIdentity)
        databaseBackend.createGroup(testGroup)

        val fetchedGroup = groupModelRepository.getByCreatorIdentityAndId(
            groupIdentity.creatorIdentity,
            GroupId(groupIdentity.groupId),
        )
        assertEquals(GroupModelDataFactory.toDataType(testGroup), fetchedGroup?.data)
    }

    @Test
    fun testGetByGroupIdentity() {
        val groupIdentityDefault = GroupIdentity("TESTTEST", 42)
        val defaultGroup = createTestDbGroup(groupIdentityDefault)
        testInsertAndGet(groupIdentityDefault, defaultGroup)

        val groupIdentityEmpty = GroupIdentity("TESTTEST", 43)
        val emptyGroup = createTestDbGroup(groupIdentityEmpty).copy(members = emptySet())
        testInsertAndGet(groupIdentityEmpty, emptyGroup)

        val groupIdentityDatesNull = GroupIdentity("TESTTEST", 44)
        val datesNullGroup = createTestDbGroup(groupIdentityDatesNull).copy(
            synchronizedAt = null,
            lastUpdate = null,
            groupDescriptionChangedAt = null,
        )
        testInsertAndGet(groupIdentityDatesNull, datesNullGroup)
    }

    @Test
    fun testMemberSetModification() {
        val groupIdentity = GroupIdentity("TESTTEST", 42)
        val defaultGroup = createTestDbGroup(groupIdentity)
        testInsertAndGet(groupIdentity, defaultGroup)

        val testData = groupModelRepository.getByGroupIdentity(groupIdentity)!!.data!!
        assertFailsWith<UnsupportedOperationException> {
            // Casting the set to a mutable set will work, but adding a new member to the set should
            // result in a runtime exception. Note that this is mainly in java code a problem, as
            // there is no cast needed to add a new member. Of course, it will result in a runtime
            // exception as well.
            (testData.otherMembers as MutableSet).add("01234567")
        }
    }

    private fun testInsertAndGet(groupIdentity: GroupIdentity, testGroup: DbGroup) {
        databaseBackend.createGroup(testGroup)

        val fetchedGroup = groupModelRepository.getByGroupIdentity(groupIdentity)
        assertEquals(GroupModelDataFactory.toDataType(testGroup), fetchedGroup?.data)
    }
}
