package ch.threema.data.repositories

import ch.threema.KoinTestRule
import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestNonceStore
import ch.threema.app.TestTaskManager
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.testutils.TestHelpers
import ch.threema.base.crypto.NonceFactory
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModelData
import ch.threema.data.storage.DbContact
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.helpers.TransactionAckTaskCodec
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.test.TestData.Identities
import ch.threema.test.TestData.PUBLIC_KEY
import ch.threema.test.TestDatabaseProvider
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.nonSecureRandomArray
import ch.threema.testhelpers.randomIdentity
import ch.threema.testhelpers.utcDate
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module

/**
 * @param contactModelData can be used in each test method and is iterated based on [ContactModelRepositoryParameterizedTest.initialValuesSet]
 *
 * @see ContactModelRepositoryTest
 */
@RunWith(value = Parameterized::class)
class ContactModelRepositoryParameterizedTest(
    private val contactModelData: ContactModelData,
) : KoinComponent {

    private val globalEventFlows: GlobalEventFlows by inject()
    private val globalEventBuses: GlobalEventBuses by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    // Services where MD is disabled
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var sqliteDatabaseBackend: SqliteDatabaseBackend
    private lateinit var multiDeviceManager: TestMultiDeviceManager
    private lateinit var taskManager: TestTaskManager
    private lateinit var nonceFactory: NonceFactory
    private lateinit var contactModelRepository: ContactModelRepository

    // Services where MD is enabled
    private lateinit var databaseProviderMd: TestDatabaseProvider
    private lateinit var sqliteDatabaseBackendMd: SqliteDatabaseBackend
    private lateinit var taskCodecMd: TransactionAckTaskCodec
    private lateinit var multiDeviceManagerMd: TestMultiDeviceManager
    private lateinit var taskManagerMd: TestTaskManager
    private lateinit var nonceFactoryMd: NonceFactory
    private lateinit var contactModelRepositoryMd: ContactModelRepository

    private enum class TestTriggerSource {
        FROM_LOCAL,
        FROM_REMOTE,
    }

    private var isMultiDeviceEnabled = false

    private val instrumentedTestModule = module {
        factory<MultiDeviceManager> {
            if (isMultiDeviceEnabled) {
                multiDeviceManagerMd
            } else {
                multiDeviceManager
            }
        }
    }

    @get:Rule
    val koinTestRule = KoinTestRule(
        modules = listOf(instrumentedTestModule),
    )

    @BeforeTest
    fun before() {
        val identityProvider = TestIdentityProvider(Identity(TestHelpers.TEST_CONTACT.identity))

        // Instantiate services where MD is disabled
        this.databaseProvider = TestDatabaseProvider()
        multiDeviceManager = TestMultiDeviceManager()
        taskManager = TestTaskManager(UnusedTaskCodec())
        nonceFactory = NonceFactory(TestNonceStore())
        sqliteDatabaseBackend = SqliteDatabaseBackend(
            databaseProvider = databaseProvider,
            identityProvider = identityProvider,
        )
        val timeProvider = TestTimeProvider()
        this.contactModelRepository = ModelRepositories(
            databaseProvider = databaseProvider,
            identityProvider = identityProvider,
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
            nonceFactory = nonceFactory,
            globalEventBuses = globalEventBuses,
            globalEventFlows = globalEventFlows,
            dispatcherProvider = dispatcherProvider,
            timeProvider = timeProvider,
        ).contacts

        // Instantiate services where MD is enabled
        this.databaseProviderMd = TestDatabaseProvider()
        this.taskCodecMd = TransactionAckTaskCodec()
        multiDeviceManagerMd = TestMultiDeviceManager(
            isMultiDeviceActive = true,
            isMdDisabledOrSupportsFs = false,
        )
        taskManagerMd = TestTaskManager(taskCodecMd)
        nonceFactoryMd = NonceFactory(TestNonceStore())
        sqliteDatabaseBackendMd = SqliteDatabaseBackend(
            databaseProvider = databaseProviderMd,
            identityProvider = identityProvider,
        )
        this.contactModelRepositoryMd = ModelRepositories(
            databaseProvider = databaseProviderMd,
            identityProvider = identityProvider,
            multiDeviceManager = multiDeviceManagerMd,
            taskManager = taskManagerMd,
            nonceFactory = nonceFactoryMd,
            globalEventBuses = globalEventBuses,
            globalEventFlows = globalEventFlows,
            dispatcherProvider = dispatcherProvider,
            timeProvider = timeProvider,
        ).contacts
    }

    /**
     * Test creation of a new contact from local.
     */
    @Test
    fun createFromLocal() {
        testCreateFromLocalOrRemote(contactModelData, TestTriggerSource.FROM_LOCAL)
    }

    /**
     * Test creation of a new contact from remote.
     */
    @Test
    fun createFromRemote() {
        testCreateFromLocalOrRemote(contactModelData, TestTriggerSource.FROM_REMOTE)
    }

    /**
     * Test creation of a new contact from sync.
     */
    @Test
    fun createFromSync() {
        testCreateFromSync(contactModelData)
    }

    /**
     * Test creation of a new contact from local twice. The first time a new contact should be
     * created. The second time a [ContactStoreException] should be thrown.
     */
    @Test
    fun createFromLocalTwice() {
        testCreateFromLocalOrRemoteTwice(contactModelData, TestTriggerSource.FROM_LOCAL)
    }

    /**
     * Test creation of a new contact from remote twice. The first time a new contact should be
     * created. The second time a [ContactStoreException] should be thrown.
     */
    @Test
    fun createFromRemoteTwice() {
        testCreateFromLocalOrRemoteTwice(contactModelData, TestTriggerSource.FROM_REMOTE)
    }

    /**
     * Test creation of a new contact from sync twice. The first time a new contact should be
     * created. The second time a [ContactStoreException] should be thrown.
     */
    @Test
    fun createFromSyncTwice() {
        testCreateFromSyncTwice(contactModelData)
    }

    @Test
    fun getByIdentityNotFound() {
        val model = contactModelRepository.getByIdentity("ABCDEFGH")
        assertNull(model)
        val modelMd = contactModelRepositoryMd.getByIdentity("ABCDEFGH")
        assertNull(modelMd)
    }

    @Test
    fun getByIdentityExisting() {
        val identity = randomIdentity()
        val publicKey = nonSecureRandomArray(32)

        // Create contact using the sqlite database backend directly
        val dbContact = getDbContact(identity, publicKey)
        sqliteDatabaseBackend.createContact(dbContact)
        sqliteDatabaseBackendMd.createContact(dbContact)

        // Fetch contact using the contact model repository
        val model = contactModelRepository.getByIdentity(identity)!!
        val modelMd = contactModelRepositoryMd.getByIdentity(identity)!!
        assertEquals(model.identity, modelMd.identity)
        assertContentEquals(model.data, modelMd.data)
        assertEquals(identity, model.identity)
        assertEquals(identity, model.data?.identity)
        assertContentEquals(publicKey, model.data?.publicKey)
    }

    @Test
    fun userCannotBeAddedAsContact() {
        assertFailsWith<InvalidContactException> {
            contactModelRepository.createFromSync(
                contactModelData = contactModelData.copy(identity = TestHelpers.TEST_CONTACT.identity),
            )
        }
    }

    private fun testCreateFromLocalOrRemote(
        contactModelData: ContactModelData,
        triggerSource: TestTriggerSource,
    ) {
        assertNull(contactModelRepository.getByIdentity(contactModelData.identity))
        assertNull(contactModelRepositoryMd.getByIdentity(contactModelData.identity))

        val (newModel, newModelMd) = runBlocking {
            when (triggerSource) {
                TestTriggerSource.FROM_LOCAL -> {
                    declareNonMdDependencies()
                    val newModel = contactModelRepository.createFromLocal(contactModelData)

                    declareMdDependencies()
                    val newModelMd = contactModelRepositoryMd.createFromLocal(contactModelData)

                    newModel to newModelMd
                }

                TestTriggerSource.FROM_REMOTE -> {
                    declareNonMdDependencies()
                    val newContactModel = contactModelRepository.createFromRemote(
                        contactModelData = contactModelData,
                        handle = UnusedTaskCodec(),
                    )

                    declareMdDependencies()
                    val newContactModelMd =
                        contactModelRepositoryMd.createFromRemote(
                            contactModelData = contactModelData,
                            handle = taskCodecMd,
                        )

                    newContactModel to newContactModelMd
                }
            }
        }

        // Assert that a transaction has been executed in the MD context
        assertEquals(1, taskCodecMd.transactionBeginCount)
        assertEquals(1, taskCodecMd.transactionCommitCount)

        val queriedModel = contactModelRepository.getByIdentity(contactModelData.identity)
        assertEquals(newModel, queriedModel)

        val queriedModelMd = contactModelRepositoryMd.getByIdentity(contactModelData.identity)
        assertEquals(newModelMd, queriedModelMd)

        assertContentEquals(contactModelData, newModel.data)
        assertContentEquals(contactModelData, newModelMd.data)

        // Reset transaction count in case this test is run several times
        taskCodecMd.transactionBeginCount = 0
        taskCodecMd.transactionCommitCount = 0
    }

    /**
     * Insert the given [contactModelData] twice (from local or remote; depending on the given
     * [triggerSource]): The first time this should create a new contact, the second time it should
     * throw a [ContactStoreException].
     */
    private fun testCreateFromLocalOrRemoteTwice(
        contactModelData: ContactModelData,
        triggerSource: TestTriggerSource,
    ) {
        assertNull(contactModelRepository.getByIdentity(contactModelData.identity))
        assertNull(contactModelRepositoryMd.getByIdentity(contactModelData.identity))

        val (runCreation, runCreationMd) = when (triggerSource) {
            TestTriggerSource.FROM_LOCAL -> {
                val runCreation = suspend {
                    declareNonMdDependencies()
                    contactModelRepository.createFromLocal(contactModelData)
                }
                val runCreationMd = suspend {
                    declareMdDependencies()
                    contactModelRepositoryMd.createFromLocal(contactModelData)
                }

                runCreation to runCreationMd
            }

            TestTriggerSource.FROM_REMOTE -> {
                val runCreation = suspend {
                    declareNonMdDependencies()
                    contactModelRepository.createFromRemote(
                        contactModelData = contactModelData,
                        handle = UnusedTaskCodec(),
                    )
                }
                val runCreationMd = suspend {
                    declareMdDependencies()
                    contactModelRepositoryMd.createFromRemote(
                        contactModelData = contactModelData,
                        handle = taskCodecMd,
                    )
                }

                runCreation to runCreationMd
            }
        }

        // Insert it for the first time
        val (newModel, newModelMd) = runBlocking {
            runCreation() to runCreationMd()
        }

        // Assert that a transaction has been executed in the MD context
        assertEquals(1, taskCodecMd.transactionBeginCount)
        assertEquals(1, taskCodecMd.transactionCommitCount)

        val queriedModel = contactModelRepository.getByIdentity(contactModelData.identity)
        assertEquals(newModel, queriedModel)

        val queriedModelMd = contactModelRepositoryMd.getByIdentity(contactModelData.identity)
        assertEquals(newModelMd, queriedModelMd)

        assertContentEquals(contactModelData, newModel.data)
        assertContentEquals(contactModelData, newModelMd.data)

        // Insert for the second time and assert that an exception is thrown
        assertFailsWith<ContactStoreException> { runBlocking { runCreation() } }
        assertFailsWith<ContactReflectException> { runBlocking { runCreationMd() } }

        // Assert that there is still only one transaction and therefore the transaction has not
        // been executed again (due to precondition failure)
        assertEquals(1, taskCodecMd.transactionBeginCount)
        assertEquals(1, taskCodecMd.transactionCommitCount)

        // Reset transaction count in case this test is run several times
        taskCodecMd.transactionBeginCount = 0
        taskCodecMd.transactionCommitCount = 0
    }

    private fun testCreateFromSync(contactModelData: ContactModelData) {
        // Assert that the contact does not exist yet
        assertNull(contactModelRepositoryMd.getByIdentity(contactModelData.identity))

        // Create the contact
        val contactModel = contactModelRepositoryMd.createFromSync(contactModelData)

        // Assert that no transactions were created and no messages were sent
        assertEquals(0, taskCodecMd.transactionBeginCount)
        assertEquals(0, taskCodecMd.transactionCommitCount)
        assertTrue(taskCodecMd.outboundMessages.isEmpty())

        // Assert that the contact data has been inserted correctly
        val addedData = contactModel.data!!
        assertContentEquals(contactModelData, addedData)

        // Reset transaction count in case this test is run several times
        taskCodecMd.transactionBeginCount = 0
        taskCodecMd.transactionCommitCount = 0
    }

    /**
     * Insert the given [contactModelData] twice from sync: The first time this should create a new
     * contact, the second time it should throw a [ContactStoreException].
     */
    private fun testCreateFromSyncTwice(contactModelData: ContactModelData) {
        // Assert that the contact does not exist yet
        assertNull(contactModelRepositoryMd.getByIdentity(contactModelData.identity))

        // Create the contact
        val contactModel = contactModelRepositoryMd.createFromSync(contactModelData)

        // Assert that no transactions were created and no messages were sent
        assertEquals(0, taskCodecMd.transactionBeginCount)
        assertEquals(0, taskCodecMd.transactionCommitCount)
        assertTrue(taskCodecMd.outboundMessages.isEmpty())

        // Assert that the contact data has been inserted correctly
        val addedData = contactModel.data!!
        assertContentEquals(contactModelData, addedData)

        // Assert that the contact data cannot be inserted again (as it already exists)
        assertFailsWith<ContactStoreException> {
            contactModelRepositoryMd.createFromSync(contactModelData)
        }

        // Reset transaction count in case this test is run several times
        taskCodecMd.transactionBeginCount = 0
        taskCodecMd.transactionCommitCount = 0
    }

    private fun declareNonMdDependencies() {
        isMultiDeviceEnabled = false
    }

    private fun declareMdDependencies() {
        isMultiDeviceEnabled = true
    }

    private fun assertContentEquals(expected: ContactModelData?, actual: ContactModelData?) {
        if (expected == null && actual == null) {
            return
        }

        if (expected == null) {
            fail("Actual data expected to be null")
        }

        if (actual == null) {
            fail("Actual data expected to be non null")
        }

        assertEquals(expected.identity, actual.identity)
        assertContentEquals(expected.publicKey, actual.publicKey)
        assertEquals(expected.createdAt, actual.createdAt)
        assertEquals(expected.firstName, actual.firstName)
        assertEquals(expected.lastName, actual.lastName)
        assertEquals(expected.nickname, actual.nickname)
        assertEquals(expected.idColor, actual.idColor)
        assertEquals(expected.verificationLevel, actual.verificationLevel)
        assertEquals(expected.workVerificationLevel, actual.workVerificationLevel)
        assertEquals(expected.identityType, actual.identityType)
        assertEquals(expected.acquaintanceLevel, actual.acquaintanceLevel)
        assertEquals(expected.activityState, actual.activityState)
        assertEquals(expected.syncState, actual.syncState)
        assertEquals(expected.featureMask, actual.featureMask)
        assertEquals(expected.readReceiptPolicy, actual.readReceiptPolicy)
        assertEquals(expected.typingIndicatorPolicy, actual.typingIndicatorPolicy)
        assertEquals(expected.notificationTriggerPolicyOverride, actual.notificationTriggerPolicyOverride)
        assertEquals(expected.androidContactLookupInfo, actual.androidContactLookupInfo)

        // TODO(ANDR-2998): Assert that notification sound policy override are set correctly

        // Just in case there are new fields added that are not explicitly compared here
        assertEquals(expected, actual)
    }

    private fun getDbContact(identity: IdentityString, publicKey: ByteArray): DbContact = DbContact(
        identity = identity,
        publicKey = publicKey,
        createdAt = Instant.ofEpochMilli(42),
        lastUpdateAt = null,
        firstName = "",
        lastName = "",
        nickname = null,
        colorIndex = 0,
        verificationLevel = VerificationLevel.UNVERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        identityType = IdentityType.REGULAR,
        acquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState = IdentityState.ACTIVE,
        syncState = ContactSyncState.INITIAL,
        featureMask = 42u,
        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        conversationVisibility = ConversationVisibility.NORMAL,
        conversationCategory = ConversationCategory.DEFAULT.serializedValue,
        androidContactLookupKey = null,
        localAvatarExpires = null,
        isRestored = false,
        profilePictureBlobId = null,
        jobTitle = null,
        department = null,
        notificationTriggerPolicyOverridePolicy = null,
        notificationTriggerPolicyOverrideExpiresAt = null,
        availabilityStatusSet = null,
        workLastFullSyncAt = null,
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun initialValuesSet() = setOf(
            getInitialContactModelData(),
            getInitialContactModelData(publicKey = PUBLIC_KEY),
            getInitialContactModelData(createdAt = Instant.ofEpochMilli(42)),
            getInitialContactModelData(identityType = IdentityType.WORK),
            getInitialContactModelData(acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED),
            getInitialContactModelData(activityState = IdentityState.INACTIVE),
            getInitialContactModelData(featureMask = 64.toULong()),
        )

        private fun getInitialContactModelData(
            identity: IdentityString = Identities.OTHER_1.value,
            publicKey: ByteArray = PUBLIC_KEY,
            createdAt: Instant = utcDate(2026, 7, 28),
            lastUpdateAt: Instant? = null,
            firstName: String = "",
            lastName: String = "",
            nickname: String? = null,
            verificationLevel: VerificationLevel = VerificationLevel.UNVERIFIED,
            workVerificationLevel: WorkVerificationLevel = WorkVerificationLevel.NONE,
            identityType: IdentityType = IdentityType.REGULAR,
            acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
            activityState: IdentityState = IdentityState.ACTIVE,
            syncState: ContactSyncState = ContactSyncState.INITIAL,
            featureMask: ULong = 0u,
            readReceiptPolicy: ReadReceiptPolicy = ReadReceiptPolicy.DEFAULT,
            typingIndicatorPolicy: TypingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
            conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
            conversationCategory: ConversationCategory = ConversationCategory.DEFAULT,
            androidContactLookupInfo: AndroidContactLookupInfo? = null,
            localAvatarExpires: Instant? = null,
            isRestored: Boolean = false,
            profilePictureBlobId: ByteArray? = null,
            jobTitle: String? = null,
            department: String? = null,
        ) = ContactModelData(
            identity = identity,
            publicKey = publicKey,
            createdAt = createdAt,
            lastUpdateAt = lastUpdateAt,
            firstName = firstName,
            lastName = lastName,
            nickname = nickname,
            idColor = IdColor.ofIdentity(identity),
            verificationLevel = verificationLevel,
            workVerificationLevel = workVerificationLevel,
            identityType = identityType,
            acquaintanceLevel = acquaintanceLevel,
            activityState = activityState,
            syncState = syncState,
            featureMask = featureMask,
            readReceiptPolicy = readReceiptPolicy,
            typingIndicatorPolicy = typingIndicatorPolicy,
            conversationVisibility = conversationVisibility,
            conversationCategory = conversationCategory,
            androidContactLookupInfo = androidContactLookupInfo,
            localAvatarExpires = localAvatarExpires,
            isRestored = isRestored,
            profilePictureBlobId = profilePictureBlobId,
            jobTitle = jobTitle,
            department = department,
            notificationTriggerPolicyOverride = null,
            availabilityStatus = AvailabilityStatus.None,
            workLastFullSyncAt = null,
        )
    }
}
