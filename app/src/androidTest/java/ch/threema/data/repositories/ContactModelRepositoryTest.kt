package ch.threema.data.repositories

import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.TestNonceStore
import ch.threema.app.TestTaskManager
import ch.threema.app.eventbus.GlobalEventBusesImpl
import ch.threema.base.crypto.NonceFactory
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.test.TestData.Identities
import ch.threema.test.TestData.PublicKeys
import ch.threema.test.TestData.createContactModelData
import ch.threema.test.TestData.createDbContact
import ch.threema.test.TestDatabaseProvider
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.TestDispatcherProvider
import ch.threema.testhelpers.TestTimeProvider
import junit.framework.TestCase.assertNotNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @see ContactModelRepositoryParameterizedTest
 */
class ContactModelRepositoryTest {

    private lateinit var sqliteDatabaseBackend: SqliteDatabaseBackend
    private lateinit var contactModelRepository: ContactModelRepository

    @BeforeTest
    fun beforeTest() {
        val testDispatcherProvider = TestDispatcherProvider()
        val testTimeProvider = TestTimeProvider()
        val identityProvider = TestIdentityProvider(
            identity = Identities.ME,
        )
        val databaseProvider = TestDatabaseProvider()
        val globalEventBuses = GlobalEventBusesImpl(
            dispatcherProvider = testDispatcherProvider,
        )
        sqliteDatabaseBackend = SqliteDatabaseBackend(
            databaseProvider = databaseProvider,
            identityProvider = identityProvider,
        )
        contactModelRepository = ModelRepositories(
            databaseProvider = databaseProvider,
            identityProvider = identityProvider,
            multiDeviceManager = TestMultiDeviceManager(
                isMultiDeviceActive = false,
            ),
            taskManager = TestTaskManager(UnusedTaskCodec()),
            nonceFactory = NonceFactory(TestNonceStore()),
            globalEventBuses = globalEventBuses,
            globalEventFlows = globalEventBuses,
            dispatcherProvider = testDispatcherProvider,
            timeProvider = testTimeProvider,
        ).contacts
    }

    @Test
    fun updateNickname() {
        // Create contact using the sqlite database backend directly
        val identity = Identities.OTHER_1
        val publicKey = PublicKeys.OTHER_1
        sqliteDatabaseBackend.createContact(
            dbContacts = arrayOf(
                createDbContact(identity, publicKey),
            ),
        )

        // Fetch model
        val contactModel = contactModelRepository.getByIdentity(identity)
        assertNotNull(contactModel)
        assertNull(contactModel!!.data?.nickname)
        contactModel.setNicknameFromSync("test-nick")
        assertEquals(
            expected = "test-nick",
            actual = contactModel.data?.nickname,
        )
    }

    @Test
    fun allExistWithAcquaintanceLevel_empty_input() {
        // act
        val allExistDirect = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = emptySet(),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        val allExistGroupOrDeleted = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = emptySet(),
            acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
        )

        // assert
        assertTrue(allExistDirect)
        assertTrue(allExistGroupOrDeleted)
    }

    @Test
    fun allExistWithAcquaintanceLevel_empty_cache_and_database() {
        // act
        val allExist = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_2.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertFalse(allExist)
    }

    /**
     * Finds none because of identities (acquaintance level actually matches)
     */
    @Test
    fun allExistWithAcquaintanceLevel_none_1() {
        // arrange
        // Contact 1 and 2 are in model cache, contact 3 and 4 are not
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_1,
                publicKey = PublicKeys.OTHER_1,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_2,
                publicKey = PublicKeys.OTHER_2,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        sqliteDatabaseBackend.createContact(
            createDbContact(
                identity = Identities.OTHER_3,
                publicKey = PublicKeys.OTHER_3,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
            createDbContact(
                identity = Identities.OTHER_4,
                publicKey = PublicKeys.OTHER_4,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        // act
        val allExist = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.ME.value,
                Identities.OTHER_5.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertFalse(allExist)
    }

    /**
     * Finds none because of acquaintance level (identities actually match)
     */
    @Test
    fun allExistWithAcquaintanceLevel_none_2() {
        // arrange
        // Contact 1 and 2 are in model cache, contact 3 and 4 are not
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_1,
                publicKey = PublicKeys.OTHER_1,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_2,
                publicKey = PublicKeys.OTHER_2,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        sqliteDatabaseBackend.createContact(
            createDbContact(
                identity = Identities.OTHER_3,
                publicKey = PublicKeys.OTHER_3,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
            createDbContact(
                identity = Identities.OTHER_4,
                publicKey = PublicKeys.OTHER_4,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        // act
        val allExist = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_3.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allExistWithAcquaintanceLevel_partial_cache() {
        // arrange
        // Contact 1, 2 and 3 are in model cache
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_1,
                publicKey = PublicKeys.OTHER_1,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_2,
                publicKey = PublicKeys.OTHER_2,
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            ),
        )
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_3,
                publicKey = PublicKeys.OTHER_3,
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            ),
        )

        // act
        val allExist = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_2.value,
                Identities.OTHER_3.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allExistWithAcquaintanceLevel_partial_database() {
        // arrange
        // Contact 1 is in model cache, contacts 2, 3, and 4 are not
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_1,
                publicKey = PublicKeys.OTHER_1,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )
        sqliteDatabaseBackend.createContact(
            createDbContact(
                identity = Identities.OTHER_2,
                publicKey = PublicKeys.OTHER_2,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
            createDbContact(
                identity = Identities.OTHER_3,
                publicKey = PublicKeys.OTHER_3,
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            ),
            createDbContact(
                identity = Identities.OTHER_4,
                publicKey = PublicKeys.OTHER_4,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        // act
        val allExist = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_2.value,
                Identities.OTHER_3.value,
                Identities.OTHER_4.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allExistWithAcquaintanceLevel_all_cache() {
        // arrange
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_1,
                publicKey = PublicKeys.OTHER_1,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_2,
                publicKey = PublicKeys.OTHER_2,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_3,
                publicKey = PublicKeys.OTHER_3,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        // act
        val allExist = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_2.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allExistWithAcquaintanceLevel_all_database() {
        // arrange
        sqliteDatabaseBackend.createContact(
            createDbContact(
                identity = Identities.OTHER_1,
                publicKey = PublicKeys.OTHER_1,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
            createDbContact(
                identity = Identities.OTHER_2,
                publicKey = PublicKeys.OTHER_2,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
            createDbContact(
                identity = Identities.OTHER_3,
                publicKey = PublicKeys.OTHER_3,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
            createDbContact(
                identity = Identities.OTHER_4,
                publicKey = PublicKeys.OTHER_4,
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            ),
            createDbContact(
                identity = Identities.OTHER_5,
                publicKey = PublicKeys.OTHER_5,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        // act
        val allExist = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_2.value,
                Identities.OTHER_3.value,
                Identities.OTHER_5.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allExistWithAcquaintanceLevel_all_cache_and_database_mix() {
        // arrange
        contactModelRepository.persistNewContact(
            createContactModelData(
                identity = Identities.OTHER_1,
                publicKey = PublicKeys.OTHER_1,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )
        sqliteDatabaseBackend.createContact(
            createDbContact(
                identity = Identities.OTHER_2,
                publicKey = PublicKeys.OTHER_2,
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            ),
            createDbContact(
                identity = Identities.OTHER_3,
                publicKey = PublicKeys.OTHER_3,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        // act
        val allExist = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_3.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allExistWithAcquaintanceLevel_all() {
        // arrange
        sqliteDatabaseBackend.createContact(
            createDbContact(
                identity = Identities.OTHER_1,
                publicKey = PublicKeys.OTHER_1,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
            createDbContact(
                identity = Identities.OTHER_2,
                publicKey = PublicKeys.OTHER_2,
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            ),
            createDbContact(
                identity = Identities.OTHER_3,
                publicKey = PublicKeys.OTHER_3,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        // act
        val allExist = contactModelRepository.allExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_3.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun countExisting() {
        // arrange
        sqliteDatabaseBackend.createContact(
            createDbContact(
                identity = Identities.OTHER_1,
                publicKey = PublicKeys.OTHER_1,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
            createDbContact(
                identity = Identities.OTHER_2,
                publicKey = PublicKeys.OTHER_2,
                acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
            ),
            createDbContact(
                identity = Identities.OTHER_3,
                publicKey = PublicKeys.OTHER_3,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
            ),
        )

        // assert
        assertEquals(
            1u,
            contactModelRepository.countExisting(setOf(Identities.OTHER_1.value)),
        )
        assertEquals(
            2u,
            contactModelRepository.countExisting(setOf(Identities.OTHER_1.value, Identities.OTHER_2.value)),
        )
        assertEquals(
            1u,
            contactModelRepository.countExisting(setOf(Identities.OTHER_2.value)),
        )
        assertEquals(
            0u,
            contactModelRepository.countExisting(setOf(Identities.OTHER_4.value)),
        )
    }
}
