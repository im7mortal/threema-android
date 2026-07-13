package ch.threema.app.restrictions

import android.content.Context
import android.os.Bundle
import ch.threema.app.R
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.license.LicenseServiceUser
import ch.threema.app.utils.ConfigUtils
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.protocol.api.work.WorkMDMSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assume
import org.junit.BeforeClass
import org.koin.core.context.startKoin
import org.koin.test.ClosingKoinTest
import org.koin.test.mock.declare

class AppRestrictionServiceImplTest : ClosingKoinTest {

    @BeforeTest
    fun setUp() {
        startKoin { }
    }

    @Test
    fun testReload_AppRestriction_NoWorkMDM() {
        val bundle = mockk<Bundle>(relaxed = true)
        val workMDMSettings = WorkMDMSettings()
        val service = AppRestrictionServiceImpl(
            appContext = mockContext(),
            mdmSettingsStore = mockk {
                every { getWorkMDMSettings() } returns workMDMSettings
            },
            applyAppRestrictionsWorkerScheduler = mockk(relaxed = true),
            getExternalMdmParameters = { bundle },
            workInfoUpdater = mockk(),
        )

        service.reload()

        // no overrides
        verify(exactly = 0) { bundle.putInt(any(), any()) }
        verify(exactly = 0) { bundle.putBoolean(any(), any()) }
        verify(exactly = 0) { bundle.putString(any(), any()) }
        verify(exactly = 0) { bundle.putLong(any(), any()) }
        verify(exactly = 0) { bundle.putDouble(any(), any()) }

        val loadedRestrictions = service.appRestrictions

        assertNotNull(loadedRestrictions)
        assertEquals(bundle, loadedRestrictions)
    }

    @Test
    fun testReload_AppRestriction_WorkMDM_OverrideFalse() {
        val bundle = mockk<Bundle>(relaxed = true) {
            every { containsKey("param1") } returns true
            every { containsKey("param2") } returns false
            every { containsKey("param3") } returns false
        }
        val mockContext = mockContext()
        val service = AppRestrictionServiceImpl(
            appContext = mockContext,
            mdmSettingsStore = mockk {
                every { getWorkMDMSettings() } returns WorkMDMSettings(
                    override = false,
                    parameters = mutableMapOf(
                        // should not be written
                        "param1" to "work-param-1",
                        // should be written
                        "param2" to 22,
                        // should be written
                        "param3" to true,
                    ),
                )
            },
            applyAppRestrictionsWorkerScheduler = mockk(relaxed = true),
            getExternalMdmParameters = { bundle },
            workInfoUpdater = mockk(),
        )

        service.reload()

        // no overrides
        verify(exactly = 1) { bundle.putInt("param2", 22) }
        verify(exactly = 1) { bundle.putBoolean("param3", true) }
        verify(exactly = 0) { bundle.putString(any(), any()) }
        verify(exactly = 0) { bundle.putLong(any(), any()) }
        verify(exactly = 0) { bundle.putDouble(any(), any()) }

        val loadedRestrictions = service.appRestrictions

        assertNotNull(loadedRestrictions)
        assertEquals(bundle, loadedRestrictions)
    }

    @Test
    fun testReload_AppRestriction_WorkMDM_OverrideTrue() {
        val bundle = mockk<Bundle>(relaxed = true) {
            every { containsKey("param1") } returns true
            every { containsKey("param2") } returns false
            every { containsKey("param3") } returns false
        }
        val service = AppRestrictionServiceImpl(
            appContext = mockContext(),
            mdmSettingsStore = mockk {
                every { getWorkMDMSettings() } returns WorkMDMSettings(
                    override = true,
                    parameters = mutableMapOf(
                        // should not be written
                        "param1" to "work-param-1",
                        // should be written
                        "param2" to 22,
                        // should be written
                        "param3" to true,
                    ),
                )
            },
            applyAppRestrictionsWorkerScheduler = mockk(relaxed = true),
            getExternalMdmParameters = { bundle },
            workInfoUpdater = mockk(),
        )

        service.reload()

        // no overrides
        verify(exactly = 1) { bundle.putInt("param2", 22) }
        verify(exactly = 1) { bundle.putBoolean("param3", true) }
        verify(exactly = 1) { bundle.putString("param1", "work-param-1") }
        verify(exactly = 0) { bundle.putLong(any(), any()) }
        verify(exactly = 0) { bundle.putDouble(any(), any()) }

        val loadedRestrictions = service.appRestrictions
        assertNotNull(loadedRestrictions)
        assertEquals(bundle, loadedRestrictions)

        assertEquals("me", service.mdmSource)
    }

    @Test
    fun testStoreWorkMDMSettings() {
        val settings = WorkMDMSettings()
        val mdmSettingsStore = mockk<MdmSettingsStore>(relaxed = true)
        val applyAppRestrictionsWorkerSchedulerMock = mockk<ApplyAppRestrictionsWorker.Scheduler>(relaxed = true)
        val service = AppRestrictionServiceImpl(
            appContext = mockContext(),
            mdmSettingsStore = mdmSettingsStore,
            applyAppRestrictionsWorkerScheduler = applyAppRestrictionsWorkerSchedulerMock,
            getExternalMdmParameters = { mockBundle() },
            workInfoUpdater = mockk(),
        )

        service.storeWorkMDMSettings(settings)

        verify(exactly = 1) { mdmSettingsStore.storeWorkMDMSettings(settings) }
        verify(exactly = 1) { applyAppRestrictionsWorkerSchedulerMock.applyAppRestrictions() }
    }

    @Test
    fun testGetEmptyWorkMDMSettings() {
        val settings = WorkMDMSettings()
        val service = AppRestrictionServiceImpl(
            appContext = mockContext(),
            mdmSettingsStore = mockk {
                every { getWorkMDMSettings() } returns settings
            },
            applyAppRestrictionsWorkerScheduler = mockk(),
            getExternalMdmParameters = { mockBundle() },
            workInfoUpdater = mockk(),
        )

        assertEquals(settings, service.getWorkMDMSettings())
        assertNull(service.mdmSource)
    }

    @Test
    fun testGetNonEmptyWorkMDMSettings() {
        val settings = WorkMDMSettings(
            parameters = mutableMapOf(
                "param-1" to 123,
            ),
        )
        val service = AppRestrictionServiceImpl(
            appContext = mockContext(),
            mdmSettingsStore = mockk {
                every { getWorkMDMSettings() } returns settings
            },
            applyAppRestrictionsWorkerScheduler = mockk(),
            getExternalMdmParameters = { mockBundle() },
            workInfoUpdater = mockk(),
        )

        assertEquals(settings, service.getWorkMDMSettings())
        assertEquals("m", service.mdmSource)
    }

    @Test
    fun testFilterNonWorkMdmParametersFromPreferences() {
        val bundleMock = mockBundle()
        val mdmSettingsStoreMock = mockk<MdmSettingsStore> {
            every { getWorkMDMSettings() } returns WorkMDMSettings(
                override = true,
                parameters = mutableMapOf(
                    "th_id_backup" to "ABCD1234",
                    "th_id_backup_password" to "T0p\$ecr3t",
                    "th_safe_password" to "T0p\$ecr3t",
                    "th_license_username" to "<username>",
                    "th_license_password" to "T0p\$ecr3t",
                    "th_safe_enable" to true,
                    "th_firstname" to "John",
                    "th_lastname" to "Doe",
                ),
            )
        }

        val service = AppRestrictionServiceImpl(
            appContext = mockContext(),
            mdmSettingsStore = mdmSettingsStoreMock,
            applyAppRestrictionsWorkerScheduler = mockk(relaxed = true),
            getExternalMdmParameters = { bundleMock },
            workInfoUpdater = mockk(),
        )

        assertEquals(
            WorkMDMSettings(
                override = true,
                parameters = mutableMapOf(
                    "th_safe_enable" to true,
                    "th_firstname" to "John",
                    "th_lastname" to "Doe",
                ),
            ),
            service.getWorkMDMSettings(),
        )

        service.reload()
        val appRestrictions = service.appRestrictions
        assertNotNull(appRestrictions)
        assertEquals(3, appRestrictions.size().toLong())
        assertTrue(appRestrictions.getBoolean("th_safe_enable"))
        assertEquals("John", appRestrictions.getString("th_firstname"))
        assertEquals("Doe", appRestrictions.getString("th_lastname"))
    }

    /**
     * This tests whether work mdm parameter override application restrictions correctly when
     * `"override"` is set to `true` in the mdm work restrictions.
     * Parameters not available in work mdm must not override application restrictions.
     */
    @Test
    fun testMergeParams_workMdmParametersMustOverrideApplicationRestrictions() {
        // Arrange
        val applicationRestrictions = mockBundle()
        val mdmWorkParameters = mutableMapOf<String, Any?>().apply {
            // Application restrictions that must not be overridden by work mdm parameters:
            applicationRestrictions.putString("th_id_backup", "restriction_id_backup") // #1
            put("th_id_backup", "work_mdm_id_backup") // #1

            applicationRestrictions.putString("th_id_backup_password", "restriction_id_backup_password") // #2
            put("th_id_backup_password", "work_mdm_id_backup_password") // #2

            applicationRestrictions.putString("th_safe_password", "restriction_safe_password") // #3
            put("th_safe_password", "work_mdm_safe_password") // #3

            applicationRestrictions.putString("th_license_password", "restriction_license_username") // #4
            put("th_license_username", "work_mdm_license_username") // #4

            applicationRestrictions.putString("th_license_username", "restriction_license_username") // #5
            put("th_license_password", "work_mdm_license_password") // #5

            // Application restrictions that will be overridden by work mdm parameters:
            applicationRestrictions.putBoolean("th_disable_screenshots", false) // #6
            put("th_disable_screenshots", true) // #6

            applicationRestrictions.putBoolean("th_disable_calls", true) // #7
            put("th_disable_calls", false) // #7

            applicationRestrictions.putString("th_nickname", "restriction_nickname") // #8
            put("th_nickname", "work_mdm_nickname") // #8

            applicationRestrictions.putString("th_web_hosts", "restriction_web_hosts") // #9
            put("th_web_hosts", "work_mdm_web_hosts") // #9

            applicationRestrictions.putInt("th_keep_messages_days", 365) // #10
            put("th_keep_messages_days", 7) // #10

            // Application restrictions that are not touched by work mdm parameters and must therefore not change:
            applicationRestrictions.putBoolean("th_disable_export", true) // #11
            applicationRestrictions.putString("th_safe_password_message", "restriction_safe_password_message") // #12

            // Work mdm parameters not set by application restrictions:
            put("th_safe_enable", true) // #13
            put("th_firstname", "work_mdm_firstname") // #14
            put("th_lastname", "work_mdm_lastname") // #15
            put("th_job_title", "work_mdm_job_title") // #16
            put("th_department", "work_mdm_department") // #17
        }
        val service = AppRestrictionServiceImpl(
            appContext = mockContext(),
            mdmSettingsStore = mockk {
                every { getWorkMDMSettings() } returns WorkMDMSettings(
                    override = true,
                    parameters = mdmWorkParameters,
                )
            },
            applyAppRestrictionsWorkerScheduler = mockk(relaxed = true),
            getExternalMdmParameters = { applicationRestrictions },
            workInfoUpdater = mockk(),
        )

        // Act
        service.reload()

        // Assert
        val appRestrictions = service.appRestrictions
        assertNotNull(appRestrictions)

        assertEquals(17, appRestrictions.size().toLong())
        // Application restrictions that must not be overridden by work mdm parameters:
        assertEquals("restriction_id_backup", appRestrictions.getString("th_id_backup"))
        assertEquals("restriction_id_backup_password", appRestrictions.getString("th_id_backup_password"))
        assertEquals("restriction_safe_password", appRestrictions.getString("th_safe_password"))
        assertEquals("restriction_license_username", appRestrictions.getString("th_license_password"))
        assertEquals("restriction_license_username", appRestrictions.getString("th_license_username"))

        // Application restrictions that are overridden by work mdm parameters:
        assertTrue(appRestrictions.getBoolean("th_disable_screenshots"))
        assertFalse(appRestrictions.getBoolean("th_disable_calls"))
        assertEquals("work_mdm_nickname", appRestrictions.getString("th_nickname"))
        assertEquals("work_mdm_web_hosts", appRestrictions.getString("th_web_hosts"))
        assertEquals(7, appRestrictions.getInt("th_keep_messages_days").toLong())

        // Application restrictions that are not set in work mdm and therefore not overridden:
        assertTrue(appRestrictions.getBoolean("th_disable_export"))
        assertEquals("restriction_safe_password_message", appRestrictions.getString("th_safe_password_message"))

        // Work mdm parameters that are not set in application restrictions:
        assertTrue(appRestrictions.getBoolean("th_safe_enable"))
        assertEquals("work_mdm_firstname", appRestrictions.getString("th_firstname"))
        assertEquals("work_mdm_lastname", appRestrictions.getString("th_lastname"))
        assertEquals("work_mdm_job_title", appRestrictions.getString("th_job_title"))
        assertEquals("work_mdm_department", appRestrictions.getString("th_department"))
    }

    @Test
    fun `update work credentials`() {
        val licenseServiceMock = mockk<LicenseServiceUser>(relaxed = true) {
            every { loadCredentials() } returns UserCredentials(
                username = "John",
                password = "Password1",
            )
        }
        declare<LicenseService<*>> { licenseServiceMock }
        val applicationRestrictions = mockBundle().apply {
            putString("th_license_username", "John")
            putString("th_license_password", "Password2")
        }
        val workInfoUpdaterMock = mockk<WorkInfoUpdater>(relaxed = true)
        val service = AppRestrictionServiceImpl(
            appContext = mockContext(),
            mdmSettingsStore = mockk {
                every { getWorkMDMSettings() } returns WorkMDMSettings(
                    override = true,
                    parameters = mutableMapOf(),
                )
            },
            applyAppRestrictionsWorkerScheduler = mockk(relaxed = true),
            getExternalMdmParameters = { applicationRestrictions },
            workInfoUpdater = workInfoUpdaterMock,
        )

        service.reload()

        verify { licenseServiceMock.saveCredentials(UserCredentials(username = "John", password = "Password2")) }
        verify { workInfoUpdaterMock.updateWorkInfo(licenseServiceMock) }
    }

    private fun mockBundle(): Bundle {
        val values = mutableMapOf<String, Any>()
        return mockk<Bundle> {
            every { containsKey(any()) } answers { values.containsKey(firstArg()) }
            every { isEmpty } answers { values.isEmpty() }
            every { size() } answers { values.size }

            every { putInt(any(), any()) } answers { values[firstArg()] = secondArg() }
            every { putLong(any(), any()) } answers { values[firstArg()] = secondArg() }
            every { putDouble(any(), any()) } answers { values[firstArg()] = secondArg() }
            every { putBoolean(any(), any()) } answers { values[firstArg()] = secondArg() }
            every { putString(any(), any()) } answers { values[firstArg()] = secondArg() }

            every { getInt(any()) } answers { values[firstArg()] as? Int? ?: 0 }
            every { getLong(any()) } answers { values[firstArg()] as? Long? ?: 0L }
            every { getDouble(any()) } answers { values[firstArg()] as? Double? ?: 0.0 }
            every { getBoolean(any()) } answers { values[firstArg()] as? Boolean? ?: false }
            every { getString(any()) } answers { values[firstArg()] as? String }
        }
    }

    private fun mockContext(): Context = mockk<Context> {
        every { getString(R.string.restriction__id_backup) } returns "th_id_backup"
        every { getString(R.string.restriction__id_backup_password) } returns "th_id_backup_password"
        every { getString(R.string.restriction__safe_password) } returns "th_safe_password"
        every { getString(R.string.restriction__license_username) } returns "th_license_username"
        every { getString(R.string.restriction__license_password) } returns "th_license_password"
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun assumeWorkBuild() {
            Assume.assumeTrue(ConfigUtils.isWorkBuild())
        }
    }
}
