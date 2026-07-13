package ch.threema.app.contacts

import android.os.Looper
import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestNonceStore
import ch.threema.app.TestTaskManager
import ch.threema.app.asynctasks.AddContactRestrictionPolicy
import ch.threema.app.asynctasks.AddOrUpdateContactBackgroundTask
import ch.threema.app.asynctasks.AlreadyVerified
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask
import ch.threema.app.asynctasks.ContactCreated
import ch.threema.app.asynctasks.ContactExists
import ch.threema.app.asynctasks.ContactModified
import ch.threema.app.asynctasks.ContactResult
import ch.threema.app.asynctasks.InvalidThreemaId
import ch.threema.app.asynctasks.RemotePublicKeyMismatch
import ch.threema.app.asynctasks.UserIdentity
import ch.threema.app.protocolsteps.Contact
import ch.threema.app.protocolsteps.ContactOrInit
import ch.threema.app.protocolsteps.Init
import ch.threema.app.protocolsteps.Invalid
import ch.threema.app.protocolsteps.UserContact
import ch.threema.app.protocolsteps.ValidContactsLookupSteps
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.base.crypto.NaCl
import ch.threema.base.crypto.NonceFactory
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.ModelRepositories
import ch.threema.domain.helpers.TransactionAckTaskCodec
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.test.TestData
import ch.threema.test.TestData.PUBLIC_KEY
import ch.threema.test.TestDatabaseProvider
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.TestDispatcherProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

class AddOrUpdateContactBackgroundTaskTest {
    private val appRestrictions: AppRestrictions = mockk {
        every { isAddContactDisabled() } returns false
    }

    private val backgroundExecutor = BackgroundExecutor()
    private lateinit var databaseProvider: TestDatabaseProvider
    private lateinit var contactModelRepository: ContactModelRepository

    @BeforeTest
    fun before() {
        databaseProvider = TestDatabaseProvider()
        val identityProviderMock = TestIdentityProvider(Identity(MY_IDENTITY))
        contactModelRepository = ModelRepositories(
            databaseProvider = databaseProvider,
            identityProvider = identityProviderMock,
            multiDeviceManager = TestMultiDeviceManager(),
            taskManager = TestTaskManager(TransactionAckTaskCodec()),
            nonceFactory = NonceFactory(TestNonceStore()),
            globalEventBuses = mockk(relaxed = true),
            globalEventFlows = mockk(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(),
        ).contacts
    }

    @Test
    fun testAddSuccessful() = runTest {
        val newIdentity = "01234567"

        testAddingContact(
            { identity ->
                Init(
                    TestData.createContactModelData(
                        identity = Identity(identity),
                        featureMask = 12u,
                        identityType = IdentityType.REGULAR,
                        activityState = IdentityState.ACTIVE,
                        verificationLevel = VerificationLevel.UNVERIFIED,
                        acquaintanceLevel = AcquaintanceLevel.DIRECT,
                    ),
                )
            },
            {
                assertIs<ContactCreated>(it)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data!!
                assertEquals(newIdentity, data.identity)
                assertContentEquals(PUBLIC_KEY, data.publicKey)
                assertEquals(12u, data.featureMask)
                assertEquals(IdentityType.REGULAR, data.identityType)
                assertEquals(IdentityState.ACTIVE, data.activityState)
                assertEquals(VerificationLevel.UNVERIFIED, data.verificationLevel)
                assertEquals(AcquaintanceLevel.DIRECT, data.acquaintanceLevel)
            },
        )
    }

    @Test
    fun testAddGroupContactSuccessful() = runTest {
        val newIdentity = "01234567"

        testAddingContact(
            { identity ->
                Init(
                    TestData.createContactModelData(
                        identity = Identity(identity),
                        featureMask = 12u,
                        identityType = IdentityType.REGULAR,
                        activityState = IdentityState.ACTIVE,
                        verificationLevel = VerificationLevel.UNVERIFIED,
                        acquaintanceLevel = AcquaintanceLevel.DIRECT,
                    ),
                )
            },
            acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            runOnFinished = {
                assertIs<ContactCreated>(it)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data!!
                assertEquals(newIdentity, data.identity)
                assertContentEquals(PUBLIC_KEY, data.publicKey)
                assertEquals(12u, data.featureMask)
                assertEquals(IdentityType.REGULAR, data.identityType)
                assertEquals(IdentityState.ACTIVE, data.activityState)
                assertEquals(VerificationLevel.UNVERIFIED, data.verificationLevel)
                assertEquals(AcquaintanceLevel.GROUP_OR_DELETED, data.acquaintanceLevel)
            },
        )
    }

    @Test
    fun testAddSuccessfulVerified() = runTest {
        val newIdentity = "01234567"

        testAddingContact(
            { identity ->
                Init(
                    TestData.createContactModelData(
                        identity = Identity(identity),
                        featureMask = 127u,
                        identityType = IdentityType.WORK,
                        activityState = IdentityState.INACTIVE,
                        verificationLevel = VerificationLevel.FULLY_VERIFIED,
                        acquaintanceLevel = AcquaintanceLevel.DIRECT,
                    ),
                )
            },
            {
                assertIs<ContactCreated>(it)
                assertEquals(newIdentity, it.contactModel.identity)
                val data = it.contactModel.data!!
                assertEquals(newIdentity, data.identity)
                assertContentEquals(PUBLIC_KEY, data.publicKey)
                assertEquals(127u, data.featureMask)
                assertEquals(IdentityType.WORK, data.identityType)
                assertEquals(IdentityState.INACTIVE, data.activityState)
                assertEquals(VerificationLevel.FULLY_VERIFIED, data.verificationLevel)
                assertEquals(AcquaintanceLevel.DIRECT, data.acquaintanceLevel)
            },
            publicKey = PUBLIC_KEY,
        )
    }

    @Test
    fun testAddMyIdentity() = runTest {
        testAddingContact(
            { identity ->
                Init(
                    TestData.createContactModelData(
                        identity = Identity(identity),
                        featureMask = 12u,
                        identityType = IdentityType.REGULAR,
                        activityState = IdentityState.ACTIVE,
                        verificationLevel = VerificationLevel.UNVERIFIED,
                        acquaintanceLevel = AcquaintanceLevel.DIRECT,
                    ),
                )
            },
            {
                assertIs<UserIdentity>(it)
            },
            newIdentity = MY_IDENTITY,
            myIdentity = MY_IDENTITY,
        )
    }

    @Test
    fun testAddPublicKeyMismatch() = runTest {
        testAddingContact(
            { identity ->
                Init(
                    TestData.createContactModelData(
                        identity = Identity(identity),
                        featureMask = 12u,
                        identityType = IdentityType.REGULAR,
                        activityState = IdentityState.ACTIVE,
                        verificationLevel = VerificationLevel.UNVERIFIED,
                        acquaintanceLevel = AcquaintanceLevel.DIRECT,
                    ),
                )
            },
            {
                assertIs<RemotePublicKeyMismatch>(it)
            },
            publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES) { 1 },
        )
    }

    @Test
    fun testAddInvalidId() = runTest {
        testAddingContact(
            { identity ->
                Invalid(identity = identity)
            },
            {
                assertIs<InvalidThreemaId>(it)
            },
        )
    }

    @Test
    fun testAddExistingContact() = runTest {
        val validContactsLookupStepsResult: (identity: IdentityString) -> ContactOrInit = { identity ->
            Init(
                TestData.createContactModelData(
                    identity = Identity(identity),
                    featureMask = 12u,
                    identityType = IdentityType.REGULAR,
                    activityState = IdentityState.ACTIVE,
                    verificationLevel = VerificationLevel.UNVERIFIED,
                    acquaintanceLevel = AcquaintanceLevel.DIRECT,
                ),
            )
        }

        // The first time adding the contact should succeed
        testAddingContact(
            validContactsLookupStepsResult,
            {
                assertIs<ContactCreated>(it)
            },
        )

        // The second time adding the contact should fail
        testAddingContact(
            validContactsLookupStepsResult,
            {
                assertIs<ContactExists>(it)
            },
        )
    }

    @Test
    fun testVerifyingAlreadyVerifiedContact() = runTest {
        val publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES) { 2 }
        val verifiedContactIdentity = "01234567"
        val contactModelMock = mockk<ContactModel> {
            every { identity } returns verifiedContactIdentity
            every { data } returns TestData.createContactModelData(
                identity = Identity(verifiedContactIdentity),
                publicKey = publicKey,
                featureMask = 12u,
                identityType = IdentityType.REGULAR,
                activityState = IdentityState.ACTIVE,
                verificationLevel = VerificationLevel.FULLY_VERIFIED,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            )
        }

        val validContactsLookupStepsResult: (identity: IdentityString) -> ContactOrInit = { identity ->
            if (identity == verifiedContactIdentity) {
                Contact(contactModelMock)
            } else {
                fail("Unexpected identity to run valid contacts lookup steps ($identity)")
            }
        }

        // The result should be already verified, as the contact is already verified
        testAddingContact(
            validContactsLookupStepsResult,
            {
                assertIs<AlreadyVerified>(it)
            },
            publicKey = publicKey,
        )
    }

    @Test
    fun testUpgradeGroupContact() = runTest {
        val newIdentity = "01234567"

        val contactModelMock = mockk<ContactModel> {
            every { identity } returns newIdentity
            every { data } returns TestData.createContactModelData(
                identity = Identity(identity),
                featureMask = 12u,
                identityType = IdentityType.REGULAR,
                activityState = IdentityState.ACTIVE,
                verificationLevel = VerificationLevel.UNVERIFIED,
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            )
            every { setAcquaintanceLevelFromLocal(AcquaintanceLevel.DIRECT) } just Runs
        }

        val validContactLookupStepsResult: (identity: IdentityString) -> ContactOrInit = { identity ->
            if (identity == newIdentity) {
                Contact(contactModelMock)
            } else {
                fail("Unexpected identity to run valid contacts lookup steps ($identity)")
            }
        }

        // When adding the contact again, it should be converted back to a direct contact
        testAddingContact(
            validContactLookupStepsResult,
            {
                assertIs<ContactModified>(it)
                assertTrue(it.acquaintanceLevelChanged)
                assertFalse(it.verificationLevelChanged)
                verify(exactly = 1) { contactModelMock.setAcquaintanceLevelFromLocal(AcquaintanceLevel.DIRECT) }
            },
            newIdentity = newIdentity,
        )
    }

    @Test
    fun testVerificationLevelUpgrade() = runTest {
        val newIdentity = "01234567"

        val contactModelMock = mockk<ContactModel> {
            every { identity } returns newIdentity
            every { data } returns TestData.createContactModelData(
                identity = Identity(identity),
                featureMask = 12u,
                identityType = IdentityType.REGULAR,
                activityState = IdentityState.ACTIVE,
                verificationLevel = VerificationLevel.UNVERIFIED,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            )
            every { setVerificationLevelFromLocal(VerificationLevel.FULLY_VERIFIED) } just Runs
        }

        val validContactsLookupStepsResult: (identity: IdentityString) -> ContactOrInit = { identity ->
            if (identity == newIdentity) {
                Contact(contactModelMock)
            } else {
                fail("Unexpected identity to run valid contacts lookup steps ($identity)")
            }
        }

        // When "adding" the contact again, it should be upgraded to "fully verified"
        testAddingContact(
            validContactsLookupStepsResult,
            { contactResult ->
                assertIs<ContactModified>(contactResult)
                assertTrue(contactResult.verificationLevelChanged)
                assertFalse(contactResult.acquaintanceLevelChanged)
                verify(exactly = 1) { contactModelMock.setVerificationLevelFromLocal(VerificationLevel.FULLY_VERIFIED) }
            },
            newIdentity = newIdentity,
            publicKey = PUBLIC_KEY,
        )
    }

    @Test
    fun testAddAndVerifyGroupContact() = runTest {
        val newIdentity = "01234567"

        val contactModelMock = mockk<ContactModel> {
            every { identity } returns newIdentity
            every { data } returns TestData.createContactModelData(
                identity = Identity(newIdentity),
                featureMask = 12u,
                identityType = IdentityType.REGULAR,
                activityState = IdentityState.ACTIVE,
                verificationLevel = VerificationLevel.UNVERIFIED,
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            )
            every { setAcquaintanceLevelFromLocal(AcquaintanceLevel.DIRECT) } just Runs
            every { setVerificationLevelFromLocal(VerificationLevel.FULLY_VERIFIED) } just Runs
        }

        val validContactsLookupStepsResult: (identity: IdentityString) -> ContactOrInit = { identity ->
            if (identity == newIdentity) {
                Contact(contactModelMock)
            } else {
                fail("Unexpected identity to run valid contacts lookup steps ($identity)")
            }
        }

        // When adding the contact that exists as an unverified group contact, it should be converted to a direct contact (that is verified)
        testAddingContact(
            validContactsLookupStepsResult,
            {
                assertIs<ContactModified>(it)
                assertTrue(it.acquaintanceLevelChanged)
                assertTrue(it.verificationLevelChanged)
                verify(exactly = 1) { contactModelMock.setAcquaintanceLevelFromLocal(AcquaintanceLevel.DIRECT) }
                verify(exactly = 1) { contactModelMock.setVerificationLevelFromLocal(VerificationLevel.FULLY_VERIFIED) }
            },
            newIdentity = newIdentity,
            publicKey = PUBLIC_KEY,
        )
    }

    @Test
    fun testThreadUsage() = runTest {
        val identity = "01234567"
        val validContactsLookupStepsResult = mockk<ValidContactsLookupSteps> {
            every { run(identity) } returns Init(
                TestData.createContactModelData(
                    identity = Identity(identity),
                ),
            )
        }

        val testThreadId = Thread.currentThread().id

        val addTask = object : AddOrUpdateContactBackgroundTask<Boolean>(
            identity = identity,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
            validContactsLookupSteps = validContactsLookupStepsResult,
            contactModelRepository = contactModelRepository,
            addContactRestrictionPolicy = AddContactRestrictionPolicy.CHECK,
            appRestrictions = appRestrictions,
            expectedPublicKey = null,
        ) {
            override fun onBefore() {
                assertEquals(testThreadId, Thread.currentThread().id)
            }

            override fun onContactResult(result: ContactResult): Boolean {
                assertIs<ContactCreated>(result)
                assertNotEquals(testThreadId, Thread.currentThread().id)
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                return true
            }

            override fun onFinished(result: Boolean) {
                assertTrue(result)
                assertEquals(Looper.getMainLooper(), Looper.myLooper())
            }
        }

        assertTrue(backgroundExecutor.executeDeferred(addTask).await())
    }

    private suspend fun testAddingContact(
        validContactsLookupStepsResult: (identity: IdentityString) -> ContactOrInit,
        runOnFinished: (result: ContactResult) -> Unit,
        newIdentity: IdentityString = "01234567",
        acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
        myIdentity: IdentityString = "00000000",
        publicKey: ByteArray? = null,
    ) {
        val validContactsLookupStepsMock: ValidContactsLookupSteps = mockk {
            every { run(any<String>()) } answers {
                validContactsLookupStepsResult(firstArg())
            }
            every { run(myIdentity) } returns UserContact(myIdentity)
        }

        val contactAdded =
            backgroundExecutor.executeDeferred(
                object : BasicAddOrUpdateContactBackgroundTask(
                    identity = newIdentity,
                    acquaintanceLevel = acquaintanceLevel,
                    validContactsLookupSteps = validContactsLookupStepsMock,
                    contactModelRepository = contactModelRepository,
                    addContactRestrictionPolicy = AddContactRestrictionPolicy.CHECK,
                    appRestrictions = appRestrictions,
                    expectedPublicKey = publicKey,
                ) {
                    override fun onFinished(result: ContactResult) {
                        runOnFinished(result)
                    }
                },
            )

        // Assert that the test is not stopped before running the background task completely
        contactAdded.await()
    }

    companion object {
        private const val MY_IDENTITY = "00000000"
    }
}
