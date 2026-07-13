package ch.threema.app.applogo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.cash.turbine.test
import ch.threema.app.files.AppLogoFileHandleProvider
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.ConfigUtils
import ch.threema.common.files.FileHandle
import ch.threema.common.plus
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest

class AppLogoProviderTest {

    @BeforeTest
    fun setUp() {
        mockkStatic(BitmapFactory::class)
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(BitmapFactory::class)
    }

    @Test
    fun `logo is considered expired when no expiration time is known`() {
        val appLogoProvider = AppLogoProvider(
            preferenceService = mockk {
                every { getAppLogoExpiresAt(THEME) } returns null
            },
            appLogoFileHandleProvider = mockk(),
            timeProvider = mockk(),
            dispatcherProvider = mockk(),
        )

        assertTrue(appLogoProvider.isAppLogoExpired(THEME))
    }

    @Test
    fun `logo is considered expired when expiration time is in the past`() {
        val appLogoProvider = AppLogoProvider(
            preferenceService = mockk {
                every { getAppLogoExpiresAt(THEME) } returns Instant.ofEpochMilli(1000)
            },
            appLogoFileHandleProvider = mockk(),
            timeProvider = mockk {
                every { get() } returns Instant.ofEpochMilli(2000)
            },
            dispatcherProvider = mockk(),
        )

        assertTrue(appLogoProvider.isAppLogoExpired(THEME))
    }

    @Test
    fun `logo is considered not expired when expiration time is in the future`() {
        val appLogoProvider = AppLogoProvider(
            preferenceService = mockk {
                every { getAppLogoExpiresAt(THEME) } returns Instant.ofEpochMilli(5000)
            },
            appLogoFileHandleProvider = mockk(),
            timeProvider = mockk {
                every { get() } returns Instant.ofEpochMilli(2000)
            },
            dispatcherProvider = mockk(),
        )

        assertFalse(appLogoProvider.isAppLogoExpired(THEME))
    }

    @Test
    fun `watch logo`() = runTest {
        val inputStreamMock = mockk<InputStream>(relaxed = true)
        val bitmapMock1 = mockk<Bitmap>()
        val bitmapMock2 = mockk<Bitmap>()
        val appLogoFileHandleProviderMock = mockk<AppLogoFileHandleProvider> {
            every { get(AppLogoFileHandleProvider.Theme.LIGHT) } returns mockk {
                every { read() } returns inputStreamMock
                every { write() } returns mockk(relaxed = true)
                every { delete() } just runs
            }
        }
        every { BitmapFactory.decodeStream(inputStreamMock) } returns bitmapMock1
        val appLogoProvider = AppLogoProvider(
            preferenceService = mockk(relaxed = true),
            appLogoFileHandleProvider = appLogoFileHandleProviderMock,
            timeProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        appLogoProvider.watchAppLogoBitmap(THEME).test {
            // We expect the bitmap to be presented when we start watching
            expectItem(bitmapMock1)

            // When a new logo is saved, a new bitmap is emitted
            every { BitmapFactory.decodeStream(inputStreamMock) } returns bitmapMock2
            appLogoProvider.saveAppLogo(File.createTempFile("test", "test"), THEME, mockk())
            expectItem(bitmapMock2)

            // When the logo is deleted, null is emitted
            every { appLogoFileHandleProviderMock.get(AppLogoFileHandleProvider.Theme.LIGHT) } returns mockk {
                every { read() } returns null
                every { delete() } just runs
            }
            appLogoProvider.clearAppLogo(THEME)
            expectItem(null)
        }
    }

    @Test
    fun `save logo with expiration time`() = runTest {
        val logoFile = File.createTempFile("test", "test")
        val outputStream = ByteArrayOutputStream()
        logoFile.writeText("TEST")
        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true)
        val appLogoFileHandleProviderMock = mockk<AppLogoFileHandleProvider> {
            every { get(AppLogoFileHandleProvider.Theme.LIGHT) } returns mockk {
                every { write() } returns outputStream
            }
        }
        val appLogoProvider = AppLogoProvider(
            preferenceService = preferenceServiceMock,
            appLogoFileHandleProvider = appLogoFileHandleProviderMock,
            timeProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        appLogoProvider.saveAppLogo(logoFile, THEME, expires = Instant.ofEpochMilli(1000))

        verify(exactly = 1) { preferenceServiceMock.setAppLogoExpiresAt(Instant.ofEpochMilli(1000), THEME) }
        assertEquals("TEST", String(outputStream.toByteArray()))
    }

    @Test
    fun `save logo without expiration time`() = runTest {
        val logoFile = File.createTempFile("test", "test")
        val outputStream = ByteArrayOutputStream()
        logoFile.writeText("TEST")
        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true)
        val appLogoFileHandleProviderMock = mockk<AppLogoFileHandleProvider> {
            every { get(AppLogoFileHandleProvider.Theme.LIGHT) } returns mockk {
                every { write() } returns outputStream
            }
        }
        val now = Instant.ofEpochMilli(1000)
        val appLogoProvider = AppLogoProvider(
            preferenceService = preferenceServiceMock,
            appLogoFileHandleProvider = appLogoFileHandleProviderMock,
            timeProvider = mockk {
                every { get() } returns now
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        appLogoProvider.saveAppLogo(logoFile, THEME, expires = null)

        verify(exactly = 1) { preferenceServiceMock.setAppLogoExpiresAt(now + 24.hours, THEME) }
        assertEquals("TEST", String(outputStream.toByteArray()))
    }

    @Test
    fun `clear logo`() = runTest {
        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true)
        val fileHandleMock = mockk<FileHandle> {
            every { delete() } just runs
        }
        val appLogoFileHandleProviderMock = mockk<AppLogoFileHandleProvider> {
            every { get(AppLogoFileHandleProvider.Theme.LIGHT) } returns fileHandleMock
        }
        val appLogoProvider = AppLogoProvider(
            preferenceService = preferenceServiceMock,
            appLogoFileHandleProvider = appLogoFileHandleProviderMock,
            timeProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        appLogoProvider.clearAppLogo(THEME)

        verify(exactly = 1) { preferenceServiceMock.setAppLogoExpiresAt(null, THEME) }
        verify(exactly = 1) { fileHandleMock.delete() }
    }

    companion object {
        private const val THEME = ConfigUtils.THEME_LIGHT
    }
}
