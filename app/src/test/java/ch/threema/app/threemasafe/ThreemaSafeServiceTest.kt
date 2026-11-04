package ch.threema.app.threemasafe

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.ApiService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.common.emptyByteArray
import ch.threema.common.toHexString
import ch.threema.data.ModelTypeCache
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.stores.IdentityStore
import ch.threema.testhelpers.TestDispatcherProvider
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.nonSecureRandomArray
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreemaSafeServiceTest {
    private val preferenceServiceMock: PreferenceService = mockk(relaxed = true)
    private val synchronizedSettingsServiceMock: SynchronizedSettingsService = mockk(relaxed = true)
    private val contactServiceMock: ContactService = mockk(relaxed = true)
    private val identityStoreMock: IdentityStore = mockk()
    private val apiService: ApiService = mockk()
    private val blockedIdentitiesServiceMock: BlockedIdentitiesService = mockk()
    private val excludedSyncIdentitiesServiceMock: ExcludedSyncIdentitiesService = mockk()

    private val contactModelRepository: ContactModelRepository = ContactModelRepository(
        cache = ModelTypeCache(),
        databaseBackend = mockk(),
        identityProvider = mockk(),
        multiDeviceManager = mockk(),
        taskManager = mockk(),
        nonceFactory = mockk(),
        globalEventBuses = mockk(),
        globalEventFlows = mockk(),
        dispatcherProvider = TestDispatcherProvider(),
    )
    private val groupModelRepository = GroupModelRepository(
        cache = ModelTypeCache(),
        databaseBackend = mockk(),
        identityProvider = mockk(),
        multiDeviceManager = mockk(),
        taskManager = mockk(),
        globalEventBuses = mockk(),
        globalEventFlows = mockk(),
        dispatcherProvider = TestDispatcherProvider(),
    )

    private lateinit var testDate1: Instant
    private lateinit var testDate2: Instant
    private var testDate1Timestamp: Long = 0
    private var testDate2Timestamp: Long = 0

    private val threemaSafeServiceImpl: ThreemaSafeServiceImpl = ThreemaSafeServiceImpl(
        /* context = */
        mockk(),
        /* preferenceService = */
        preferenceServiceMock,
        /* synchronizedSettingsService = */
        synchronizedSettingsServiceMock,
        /* userService = */
        mockk(relaxed = true),
        /* contactService = */
        contactServiceMock,
        /* localeService = */
        mockk(),
        /* blockedIdentitiesService = */
        blockedIdentitiesServiceMock,
        /* excludedSyncIdentitiesService = */
        excludedSyncIdentitiesServiceMock,
        /* profilePictureRecipientsService = */
        mockk(),
        /* distributionListModelFactory = */
        mockk(),
        /* distributionListMemberModelFactory */
        mockk(),
        /* identityStore = */
        identityStoreMock,
        /* apiService = */
        apiService,
        /* apiConnector = */
        mockk(),
        /* contactModelRepository = */
        contactModelRepository,
        /* groupModelRepository = */
        groupModelRepository,
        /* okHttpClient = */
        mockOkHttpClient { mockk() },
        /* appRestrictions = */
        mockk(),
        /* validContactsLookupSteps = */
        mockk(),
        /* threemaSafeBackupCreator = */
        mockk(),
    )

    @Suppress("DEPRECATION")
    @BeforeTest
    fun prepareMocks() {
        every { identityStoreMock.getPrivateKey() } returns TEST_PRIVATE_KEY_BYTES
        every { identityStoreMock.getIdentityString() } returns "01234567"
        every { blockedIdentitiesServiceMock.getAllBlockedIdentities() } returns emptySet()
        every { excludedSyncIdentitiesServiceMock.getAllExcludedIdentities() } returns emptySet()

        every { threemaSafeServiceImpl.threemaSafeBackupId } answers { callOriginal() }
        every { threemaSafeServiceImpl.threemaSafeEncryptionKey } answers { callOriginal() }

        val localeSwitzerland = Locale("de", "CH")
        Locale.setDefault(localeSwitzerland)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Zurich"))

        testDate1 = LocalDateTime.of(2020, 12, 1, 13, 14, 15)
            .atZone(ZoneId.of("Europe/Zurich"))
            .toInstant()
        testDate2 = LocalDateTime.of(2021, 12, 1, 13, 14, 15)
            .atZone(ZoneId.of("Europe/Zurich"))
            .toInstant()
        testDate1Timestamp = 1606824855000L
        testDate2Timestamp = 1638360855000L
    }

    @Test
    fun testDeriveMasterKey() {
        // arrange
        val threemaSafeServiceMock = mockk<ThreemaSafeServiceImpl>()
        every { threemaSafeServiceMock.deriveMasterKey(any(), any()) } answers { callOriginal() }

        // act
        // Test case as defined in specification (see confluence)
        val masterKey = threemaSafeServiceMock.deriveMasterKey("shootdeathstar", "ECHOECHO")!!
        val masterKeyHex: String = masterKey.toHexString()

        // assert
        assertEquals(MASTER_KEY_HEX, masterKeyHex)
    }

    @Test
    fun testGetThreemaSafeBackupIdNull() {
        every { preferenceServiceMock.getThreemaSafeMasterKey() } returns null
        val backupId1: ByteArray? = threemaSafeServiceImpl.threemaSafeBackupId
        assertNull(backupId1)

        every { preferenceServiceMock.getThreemaSafeMasterKey() } returns emptyByteArray()
        val backupId2: ByteArray? = threemaSafeServiceImpl.threemaSafeBackupId
        assertNull(backupId2)

        every { preferenceServiceMock.getThreemaSafeMasterKey() } returns nonSecureRandomArray(32)
        val backupId3: ByteArray? = threemaSafeServiceImpl.threemaSafeBackupId
        assertNull(backupId3)
    }

    @Test
    fun testGetThreemaSafeBackupId() {
        every { preferenceServiceMock.getThreemaSafeMasterKey() } returns MASTER_KEY_HEX.hexToByteArray()

        val backupId = threemaSafeServiceImpl.threemaSafeBackupId!!
        val backupIdHex = backupId.toHexString()
        assertEquals(
            expected = "066384d3695fbbd9f31a7d533900fd0cd8d1373beb6a28678522d2a49980c9c3",
            actual = backupIdHex,
        )
    }

    @Test
    fun testGetThreemaSafeEncryptionKeyNull() {
        every { preferenceServiceMock.getThreemaSafeMasterKey() } returns null
        val encryptionKey1: ByteArray? = threemaSafeServiceImpl.threemaSafeEncryptionKey
        assertNull(encryptionKey1)

        every { preferenceServiceMock.getThreemaSafeMasterKey() } returns emptyByteArray()
        val encryptionKey2: ByteArray? = threemaSafeServiceImpl.threemaSafeEncryptionKey
        assertNull(encryptionKey2)

        every { preferenceServiceMock.getThreemaSafeMasterKey() } returns nonSecureRandomArray(32)
        val encryptionKey3: ByteArray? = threemaSafeServiceImpl.threemaSafeEncryptionKey
        assertNull(encryptionKey3)
    }

    @Test
    fun testGetThreemaSafeEncryptionKey() {
        every { preferenceServiceMock.getThreemaSafeMasterKey() } returns MASTER_KEY_HEX.hexToByteArray()

        val encryptionKey = threemaSafeServiceImpl.threemaSafeEncryptionKey!!
        val encryptionKeyHex = encryptionKey.toHexString()
        assertEquals(
            expected = "51c3d8d752fb6e1fd3199ead7f0895d6e3893ff691f2a5ee1976ed0897fc2f66",
            actual = encryptionKeyHex,
        )
    }

    companion object {
        // Test vector: Password "shootdeathstar" and salt "ECHOECHO" should result in this master key
        private const val MASTER_KEY_HEX: String =
            "066384d3695fbbd9f31a7d533900fd0cd8d1373beb6a28678522d2a49980c9c351c3d8d752fb6e1fd3199ead7f0895d6e3893ff691f2a5ee1976ed0897fc2f66"

        // Test data
        private val TEST_PRIVATE_KEY_BYTES: ByteArray = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8,
            100, 101, 102, 103, 104, 105, 106, 107,
            1, 2, 3, 4, 5, 6, 7, 8,
            100, 101, 102, 103, 104, 105, 106, 107,
        )
    }
}
