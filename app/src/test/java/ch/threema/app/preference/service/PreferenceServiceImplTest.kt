package ch.threema.app.preference.service

import android.content.Context
import androidx.annotation.StringRes
import ch.threema.app.R
import ch.threema.app.backupcenter.models.LastLocalDataBackupInfo
import ch.threema.app.backupcenter.models.LocalDataBackupPassword
import ch.threema.app.services.ContactService
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.common.bytes
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory
import ch.threema.domain.protocol.api.work.WorkOrganization
import ch.threema.test.TestContext
import ch.threema.testhelpers.TestDispatcherProvider
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
        preferenceStoreMock = mockk(relaxed = true) {
            every { getBoolean(any()) } returns false
            every { getBoolean(any(), any()) } answers { secondArg() }
            every { getStringSet(any()) } returns null
            every { getInstant(any()) } returns null
        }
        encryptedPreferenceStoreMock = mockk(relaxed = true) {
            every { getString(any()) } returns null
        }
        timeProvider = TestTimeProvider(initialTimestamp = 1234L)
        preferenceService = PreferenceServiceImpl(
            appContext = appContextMock,
            preferenceStore = preferenceStoreMock,
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            dispatcherProvider = TestDispatcherProvider(),
            timeProvider = timeProvider,
        )
    }

    private fun getKeyName(@StringRes key: Int): String =
        TestContext.getString(key)

    private fun mockStoredString(keyId: Int, value: String?) {
        every { preferenceStoreMock.getString(getKeyName(keyId)) } returns value
    }

    private fun mockStoredStringSet(keyId: Int, value: Set<String>?) {
        every { preferenceStoreMock.getStringSet(getKeyName(keyId)) } returns value
    }

    private fun mockStoredBoolean(keyId: Int, value: Boolean) {
        every { preferenceStoreMock.getBoolean(getKeyName(keyId)) } returns value
        every { preferenceStoreMock.getBoolean(getKeyName(keyId), any()) } returns value
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

    private fun verifyStringSetStored(keyId: Int, value: Set<String>) {
        verify { preferenceStoreMock.save(getKeyName(keyId), value) }
    }

    private fun verifyBooleanStored(keyId: Int, value: Boolean) {
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
    fun `get theme`() {
        assertEquals(PreferenceService.Theme.FOLLOW_SYSTEM, preferenceService.getTheme())

        mockStoredString(R.string.preferences__theme, "0")
        assertEquals(PreferenceService.Theme.LIGHT, preferenceService.getTheme())

        mockStoredString(R.string.preferences__theme, "1")
        assertEquals(PreferenceService.Theme.DARK, preferenceService.getTheme())

        mockStoredString(R.string.preferences__theme, "2")
        assertEquals(PreferenceService.Theme.FOLLOW_SYSTEM, preferenceService.getTheme())
    }

    @Test
    fun `set theme`() {
        preferenceService.setTheme(PreferenceService.Theme.LIGHT)
        verifyStringStored(R.string.preferences__theme, "0")

        preferenceService.setTheme(PreferenceService.Theme.DARK)
        verifyStringStored(R.string.preferences__theme, "1")

        preferenceService.setTheme(PreferenceService.Theme.FOLLOW_SYSTEM)
        verifyStringStored(R.string.preferences__theme, "2")
    }

    @Test
    fun `get emoji style`() {
        assertEquals(PreferenceService.EmojiStyle.DEFAULT, preferenceService.getEmojiStyle())

        mockStoredString(R.string.preferences__emoji_style, "1")
        assertEquals(PreferenceService.EmojiStyle.ANDROID, preferenceService.getEmojiStyle())

        mockStoredString(R.string.preferences__emoji_style, "0")
        assertEquals(PreferenceService.EmojiStyle.DEFAULT, preferenceService.getEmojiStyle())
    }

    @Test
    fun `set emoji style`() {
        preferenceService.setEmojiStyle(PreferenceService.EmojiStyle.DEFAULT)
        verifyStringStored(R.string.preferences__emoji_style, "0")

        preferenceService.setEmojiStyle(PreferenceService.EmojiStyle.ANDROID)
        verifyStringStored(R.string.preferences__emoji_style, "1")
    }

    @Test
    fun `is bigger single emojis`() {
        assertTrue(preferenceService.isBiggerSingleEmojis())

        mockStoredBoolean(R.string.preferences__bigger_single_emojis, false)
        assertFalse(preferenceService.isBiggerSingleEmojis())

        mockStoredBoolean(R.string.preferences__bigger_single_emojis, true)
        assertTrue(preferenceService.isBiggerSingleEmojis())
    }

    @Test
    fun `set bigger single emojis`() {
        preferenceService.setBiggerSingleEmojis(true)
        verifyBooleanStored(R.string.preferences__bigger_single_emojis, true)

        preferenceService.setBiggerSingleEmojis(false)
        verifyBooleanStored(R.string.preferences__bigger_single_emojis, false)
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

    @Test
    fun `get auto download types for mobile`() {
        assertEquals(
            setOf(
                PreferenceService.AutoDownloadType.IMAGE,
            ),
            preferenceService.getMobileAutoDownload(),
        )

        mockStoredStringSet(
            R.string.preferences__auto_download_mobile,
            setOf("3", "2"),
        )
        assertEquals(
            setOf(
                PreferenceService.AutoDownloadType.VOICE_MESSAGE,
                PreferenceService.AutoDownloadType.VIDEO,
            ),
            preferenceService.getMobileAutoDownload(),
        )

        mockStoredStringSet(
            R.string.preferences__auto_download_mobile,
            setOf("1", "8"),
        )
        assertEquals(
            setOf(
                PreferenceService.AutoDownloadType.IMAGE,
                PreferenceService.AutoDownloadType.FILE,
            ),
            preferenceService.getMobileAutoDownload(),
        )
    }

    @Test
    fun `set auto download types for mobile`() {
        preferenceService.setMobileAutoDownload(
            setOf(
                PreferenceService.AutoDownloadType.IMAGE,
                PreferenceService.AutoDownloadType.VOICE_MESSAGE,
                PreferenceService.AutoDownloadType.VIDEO,
                PreferenceService.AutoDownloadType.FILE,
            ),
        )
        verifyStringSetStored(
            R.string.preferences__auto_download_mobile,
            setOf("1", "3", "2", "8"),
        )

        preferenceService.setMobileAutoDownload(
            setOf(
                PreferenceService.AutoDownloadType.VIDEO,
            ),
        )
        verifyStringSetStored(
            R.string.preferences__auto_download_mobile,
            setOf("2"),
        )
    }

    @Test
    fun `get auto download types for wifi`() {
        assertEquals(
            setOf(
                PreferenceService.AutoDownloadType.IMAGE,
                PreferenceService.AutoDownloadType.VOICE_MESSAGE,
                PreferenceService.AutoDownloadType.VIDEO,
                PreferenceService.AutoDownloadType.FILE,
            ),
            preferenceService.getWifiAutoDownload(),
        )

        mockStoredStringSet(
            R.string.preferences__auto_download_wifi,
            setOf("3", "2"),
        )
        assertEquals(
            setOf(
                PreferenceService.AutoDownloadType.VOICE_MESSAGE,
                PreferenceService.AutoDownloadType.VIDEO,
            ),
            preferenceService.getWifiAutoDownload(),
        )

        mockStoredStringSet(
            R.string.preferences__auto_download_wifi,
            setOf("1", "8"),
        )
        assertEquals(
            setOf(
                PreferenceService.AutoDownloadType.IMAGE,
                PreferenceService.AutoDownloadType.FILE,
            ),
            preferenceService.getWifiAutoDownload(),
        )
    }

    @Test
    fun `set auto download types for wifi`() {
        preferenceService.setWifiAutoDownload(
            setOf(
                PreferenceService.AutoDownloadType.IMAGE,
                PreferenceService.AutoDownloadType.VOICE_MESSAGE,
                PreferenceService.AutoDownloadType.VIDEO,
                PreferenceService.AutoDownloadType.FILE,
            ),
        )
        verifyStringSetStored(
            R.string.preferences__auto_download_wifi,
            setOf("1", "3", "2", "8"),
        )

        preferenceService.setWifiAutoDownload(
            setOf(
                PreferenceService.AutoDownloadType.IMAGE,
            ),
        )
        verifyStringSetStored(
            R.string.preferences__auto_download_wifi,
            setOf("1"),
        )
    }

    @Test
    fun `has requested notification permission`() {
        assertFalse(preferenceService.hasRequestedNotificationPermission())
        mockStoredInstant(R.string.preferences__last_notification_request_timestamp, Instant.ofEpochMilli(123))
        assertTrue(preferenceService.hasRequestedNotificationPermission())
    }

    @Test
    fun `set has requested notification permission`() {
        preferenceService.setHasRequestedNotificationPermission(requested = true)
        verifyInstantStored(R.string.preferences__last_notification_request_timestamp, timeProvider.get())
    }

    @Test
    fun `get local backup password`() {
        assertNull(preferenceService.getLocalDataBackupPassword())

        val password = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ01"
        mockStoredEncryptedString(R.string.preferences__local_backup_password, password)
        assertEquals(LocalDataBackupPassword(password), preferenceService.getLocalDataBackupPassword())
    }

    @Test
    fun `set local backup password`() {
        val password = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ01"
        preferenceService.setLocalDataBackupPassword(LocalDataBackupPassword(password))
        verifyEncryptedStringStored(R.string.preferences__local_backup_password, password)
    }

    @Test
    fun `get last local backup info`() {
        assertNull(preferenceService.getLastLocalDataBackupInfo())
        mockStoredString(
            R.string.preferences__last_local_backup_info,
            """{"createdAt":1234,"size":9999,"mediaIncluded":true,"location":"some-directory"}""",
        )
        assertEquals(
            LastLocalDataBackupInfo(
                createdAt = Instant.ofEpochMilli(1234),
                size = 9999.bytes,
                mediaIncluded = true,
                location = "some-directory",
            ),
            preferenceService.getLastLocalDataBackupInfo(),
        )
    }

    @Test
    fun `set last local backup info`() {
        preferenceService.setLastLocalDataBackupInfo(
            LastLocalDataBackupInfo(
                createdAt = Instant.ofEpochMilli(1234),
                size = 9999.bytes,
                mediaIncluded = true,
                location = "some-directory",
            ),
        )
        verifyStringStored(
            R.string.preferences__last_local_backup_info,
            """{"createdAt":1234,"size":9999,"mediaIncluded":true,"location":"some-directory"}""",
        )
    }

    @Test
    fun `get contact name sorting order`() {
        assertEquals(PreferenceService.ContactSortOrder.LAST_NAME, preferenceService.getContactSortOrder())

        mockStoredString(R.string.preferences__contact_sorting, appContextMock.getString(R.string.contact_sorting__first_name))
        assertEquals(PreferenceService.ContactSortOrder.FIRST_NAME, preferenceService.getContactSortOrder())

        mockStoredString(R.string.preferences__contact_sorting, appContextMock.getString(R.string.contact_sorting__last_name))
        assertEquals(PreferenceService.ContactSortOrder.LAST_NAME, preferenceService.getContactSortOrder())
    }
}
