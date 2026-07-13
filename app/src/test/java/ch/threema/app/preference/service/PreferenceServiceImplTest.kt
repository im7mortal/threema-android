package ch.threema.app.preference.service

import android.content.Context
import androidx.annotation.StringRes
import ch.threema.app.R
import ch.threema.app.services.ContactService
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory
import ch.threema.domain.protocol.api.work.WorkOrganization
import ch.threema.test.TestContext
import ch.threema.testhelpers.TestTimeProvider
import ch.threema.testhelpers.utcDate
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PreferenceServiceImplTest {

    private lateinit var appContextMock: Context
    private lateinit var preferenceStoreMock: PreferenceStore
    private lateinit var encryptedPreferenceStoreMock: EncryptedPreferenceStore
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var preferenceService: PreferenceService

    @BeforeTest
    fun setUp() {
        appContextMock = TestContext.create()
        preferenceStoreMock = mockk(relaxed = true)
        encryptedPreferenceStoreMock = mockk(relaxed = true)
        timeProvider = TestTimeProvider(initialTimestamp = 1234L)
        preferenceService = PreferenceServiceImpl(
            appContext = appContextMock,
            preferenceStore = preferenceStoreMock,
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            timeProvider = timeProvider,
        )
    }

    private fun getKeyName(@StringRes key: Int): String =
        TestContext.getString(key)

    private fun mockStoredString(keyId: Int, value: String?) {
        every { preferenceStoreMock.getString(getKeyName(keyId)) } returns value
    }

    private fun mockStoredEncryptedString(keyId: Int, value: String?) {
        every { encryptedPreferenceStoreMock.getString(getKeyName(keyId)) } returns value
    }

    private fun mockStoredInt(keyId: Int, value: Int) {
        every { preferenceStoreMock.getInt(getKeyName(keyId)) } returns value
    }

    private fun mockStoredInstant(keyId: Int, value: Instant?) {
        every { preferenceStoreMock.getInstant(getKeyName(keyId)) } returns value
    }

    private fun verifyStringStored(keyId: Int, value: String) {
        verify { preferenceStoreMock.save(getKeyName(keyId), value) }
    }

    private fun verifyEncryptedStringStored(keyId: Int, value: String) {
        verify { encryptedPreferenceStoreMock.save(getKeyName(keyId), value) }
    }

    private fun verifyIntStored(keyId: Int, value: Int) {
        verify { preferenceStoreMock.save(getKeyName(keyId), value) }
    }

    private fun verifyInstantStored(keyId: Int, value: Instant?) {
        verify { preferenceStoreMock.save(getKeyName(keyId), value) }
    }

    @Test
    fun `get app lock grace time`() {
        mockStoredString(R.string.preferences__pin_lock_grace_time, "-1")
        assertNull(preferenceService.getAppLockGraceTime())

        mockStoredString(R.string.preferences__pin_lock_grace_time, "30")
        assertEquals(
            30.seconds,
            preferenceService.getAppLockGraceTime(),
        )

        mockStoredString(R.string.preferences__pin_lock_grace_time, null)
        assertNull(preferenceService.getAppLockGraceTime())
    }

    @Test
    fun `get identity state sync interval`() {
        mockStoredInt(R.string.preferences__identity_states_check_interval, -1)
        assertEquals(
            1.days,
            preferenceService.getIdentityStateSyncInterval(),
        )

        mockStoredInt(R.string.preferences__identity_states_check_interval, 2 * 60)
        assertEquals(
            2.minutes,
            preferenceService.getIdentityStateSyncInterval(),
        )

        mockStoredInt(R.string.preferences__identity_states_check_interval, 0)
        assertEquals(
            1.days,
            preferenceService.getIdentityStateSyncInterval(),
        )
    }

    @Test
    fun `set identity state sync interval`() {
        preferenceService.setIdentityStateSyncInterval(12.minutes)

        verifyIntStored(R.string.preferences__identity_states_check_interval, 12 * 60)
    }

    @Test
    fun `get work sync check interval`() {
        mockStoredInt(R.string.preferences__work_sync_check_interval, -1)
        assertEquals(
            1.days,
            preferenceService.getWorkSyncCheckInterval(),
        )

        mockStoredInt(R.string.preferences__work_sync_check_interval, 2 * 60)
        assertEquals(
            2.minutes,
            preferenceService.getWorkSyncCheckInterval(),
        )

        mockStoredInt(R.string.preferences__work_sync_check_interval, 0)
        assertEquals(
            1.days,
            preferenceService.getWorkSyncCheckInterval(),
        )
    }

    @Test
    fun `set work sync check interval`() {
        preferenceService.setWorkSyncCheckInterval(12.minutes)

        verifyIntStored(R.string.preferences__work_sync_check_interval, 12 * 60)
    }

    @Test
    fun `get profile pic upload data`() {
        every {
            encryptedPreferenceStoreMock.getString(getKeyName(R.string.preferences__profile_pic_upload_data))
        } returns buildJsonObject {
            put("id", "AQIDBA==")
            put("key", "CgsMDQ==")
            put("size", 1000)
        }
            .toString()

        val profilePicUploadData = preferenceService.getProfilePicUploadData()

        assertNotNull(profilePicUploadData)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), profilePicUploadData.blobId)
        assertContentEquals(byteArrayOf(10, 11, 12, 13), profilePicUploadData.encryptionKey)
        assertEquals(1000, profilePicUploadData.size)
    }

    @Test
    fun `set profile pic upload data`() {
        val uploadDate = utcDate(2026, 5, 1)
        preferenceService.setProfilePicUploadData(
            ContactService.ProfilePictureUploadData().apply {
                blobId = byteArrayOf(1, 2, 3, 4)
                encryptionKey = byteArrayOf(10, 11, 12, 13)
                size = 1000
                uploadedAt = uploadDate.toEpochMilli()
            },
        )

        val expectedJsonString = buildJsonObject {
            put("id", "AQIDBA==")
            put("key", "CgsMDQ==")
            put("size", 1000)
        }
            .toString()
        verify {
            encryptedPreferenceStoreMock.save(
                getKeyName(R.string.preferences__profile_pic_upload_data),
                expectedJsonString,
            )
        }
        verifyInstantStored(R.string.preferences__profile_pic_upload_date, uploadDate)
    }

    @Test
    fun `is backup warning dismissed`() {
        mockStoredInstant(R.string.preferences__backup_warning_dismissed_time, null)
        assertFalse(preferenceService.isBackupWarningDismissed())

        mockStoredInstant(R.string.preferences__backup_warning_dismissed_time, timeProvider.get())
        assertTrue(preferenceService.isBackupWarningDismissed())
    }

    @Test
    fun `set backup warning dismissed`() {
        preferenceService.setBackupWarningDismissed(true)
        verifyInstantStored(R.string.preferences__backup_warning_dismissed_time, timeProvider.get())

        preferenceService.setBackupWarningDismissed(false)
        verifyInstantStored(R.string.preferences__backup_warning_dismissed_time, null)
    }

    @Test
    fun `get lock mechanism`() {
        assertEquals(PreferenceService.LockMechanism.NONE, preferenceService.getLockMechanism())
        assertFalse(preferenceService.hasLockMechanism())

        mockStoredString(R.string.preferences__lock_mechanism, "biometric")
        assertEquals(PreferenceService.LockMechanism.BIOMETRIC, preferenceService.getLockMechanism())
        assertTrue(preferenceService.hasLockMechanism())

        mockStoredString(R.string.preferences__lock_mechanism, "pin")
        assertEquals(PreferenceService.LockMechanism.PIN, preferenceService.getLockMechanism())
        assertTrue(preferenceService.hasLockMechanism())

        mockStoredString(R.string.preferences__lock_mechanism, "system")
        assertEquals(PreferenceService.LockMechanism.SYSTEM, preferenceService.getLockMechanism())
        assertTrue(preferenceService.hasLockMechanism())

        mockStoredString(R.string.preferences__lock_mechanism, "none")
        assertEquals(PreferenceService.LockMechanism.NONE, preferenceService.getLockMechanism())
        assertFalse(preferenceService.hasLockMechanism())
    }

    @Test
    fun `set lock mechanism`() {
        preferenceService.setLockMechanism(PreferenceService.LockMechanism.BIOMETRIC)
        verifyStringStored(R.string.preferences__lock_mechanism, "biometric")

        preferenceService.setLockMechanism(PreferenceService.LockMechanism.NONE)
        verifyStringStored(R.string.preferences__lock_mechanism, "none")

        preferenceService.setLockMechanism(PreferenceService.LockMechanism.PIN)
        verifyStringStored(R.string.preferences__lock_mechanism, "pin")

        preferenceService.setLockMechanism(PreferenceService.LockMechanism.SYSTEM)
        verifyStringStored(R.string.preferences__lock_mechanism, "system")
    }

    @Test
    fun `get media gallery content types`() {
        assertContentEquals(
            booleanArrayOf(true, true, true, true, true, true),
            preferenceService.getMediaGalleryContentTypes(),
        )

        mockStoredString(R.string.preferences__media_gallery_content_types, "[true,false,true,false,false]")
        assertContentEquals(
            booleanArrayOf(true, false, true, false, false, true),
            preferenceService.getMediaGalleryContentTypes(),
        )
    }

    @Test
    fun `set media gallery content types`() {
        preferenceService.setMediaGalleryContentTypes(booleanArrayOf(true, false, false, true, false, true))
        verifyStringStored(R.string.preferences__media_gallery_content_types, "[true,false,false,true,false,true]")
    }

    @Test
    fun `get work directory categories`() {
        assertEquals(
            emptyList(),
            preferenceService.getWorkDirectoryCategories(),
        )

        mockStoredEncryptedString(
            R.string.preferences__work_directory_categories,
            """[{"id":"ID1","name":"Name1"},{"id":"ID2","name":null},{"id":"ID3"}]""",
        )
        assertEquals(
            listOf(
                WorkDirectoryCategory(id = "ID1", name = "Name1"),
                WorkDirectoryCategory(id = "ID2", name = null),
                WorkDirectoryCategory(id = "ID3", name = null),
            ),
            preferenceService.getWorkDirectoryCategories(),
        )
    }

    @Test
    fun `set work directory categories`() {
        preferenceService.setWorkDirectoryCategories(
            listOf(
                WorkDirectoryCategory(id = "ID1", name = "Name1"),
                WorkDirectoryCategory(id = "ID2", name = "Name2"),
                WorkDirectoryCategory(id = "ID3", name = null),
            ),
        )
        verifyEncryptedStringStored(
            R.string.preferences__work_directory_categories,
            """[{"id":"ID1","name":"Name1"},{"id":"ID2","name":"Name2"},{"id":"ID3"}]""",
        )
    }

    @Test
    fun `get work organization`() {
        assertNull(preferenceService.getWorkOrganization())

        mockStoredEncryptedString(R.string.preferences__work_directory_organization, """{"name":"My Org"}""")
        assertEquals(
            WorkOrganization(name = "My Org"),
            preferenceService.getWorkOrganization(),
        )
    }

    @Test
    fun `set work organization`() {
        preferenceService.setWorkOrganization(WorkOrganization(name = "My Org"))
        verifyEncryptedStringStored(
            R.string.preferences__work_directory_organization,
            """{"name":"My Org"}""",
        )
    }
}
