package ch.threema.app.applogo

import ch.threema.app.services.FileService
import ch.threema.testhelpers.buildResponse
import ch.threema.testhelpers.mockNoRequestOkHttpClient
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody

class UpdateAppLogoUseCaseTest {
    @Test
    fun `no stored expiration, remote provides expiration`() = runTest {
        val lightFile = File.createTempFile("logo-light", "test")
        val darkFile = File.createTempFile("logo-dark", "test")
        val appLogoProviderMock = mockk<AppLogoProvider> {
            coEvery { saveAppLogo(any(), "0", any()) } answers {
                assertEquals(LIGHT_LOGO_CONTENT, firstArg<File>().readText())
            }
            coEvery { saveAppLogo(any(), "1", any()) } answers {
                assertEquals(DARK_LOGO_CONTENT, firstArg<File>().readText())
            }
            every { isAppLogoExpired(any()) } returns true
        }
        val fileServiceMock = mockk<FileService> {
            every { createTempFile(any(), any()) } returnsMany listOf(lightFile, darkFile)
        }
        val updateAppLogoUseCase = UpdateAppLogoUseCase(
            fileService = fileServiceMock,
            appLogoProvider = appLogoProviderMock,
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("GET", request.method)
                request.buildResponse {
                    header("Expires", "Tue, 05 Aug 2025 07:28:00 GMT")
                    body(
                        when (val url = request.url.toString()) {
                            LIGHT_URL -> LIGHT_LOGO_CONTENT
                            DARK_URL -> DARK_LOGO_CONTENT
                            else -> fail("Unexpected URL $url")
                        }
                            .toResponseBody(),
                    )
                }
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        updateAppLogoUseCase.call(
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
        )

        coVerify { appLogoProviderMock.saveAppLogo(lightFile, "0", Instant.parse("2025-08-05T07:28:00Z")) }
        coVerify { appLogoProviderMock.saveAppLogo(darkFile, "1", Instant.parse("2025-08-05T07:28:00Z")) }
        assertFalse(lightFile.exists())
        assertFalse(darkFile.exists())
    }

    @Test
    fun `no stored expiration, remote provides invalid expiration`() = runTest {
        val fileServiceMock = mockk<FileService> {
            every { createTempFile(any(), any()) } returns File.createTempFile("logo", "test")
        }
        val appLogoProviderMock = mockk<AppLogoProvider> {
            coEvery { saveAppLogo(any(), "0", any()) } answers {
                assertEquals(LIGHT_LOGO_CONTENT, firstArg<File>().readText())
            }
            coEvery { saveAppLogo(any(), "1", any()) } answers {
                assertEquals(DARK_LOGO_CONTENT, firstArg<File>().readText())
            }
            every { isAppLogoExpired(any()) } returns true
        }
        val updateAppLogoUseCase = UpdateAppLogoUseCase(
            fileService = fileServiceMock,
            appLogoProvider = appLogoProviderMock,
            okHttpClient = mockOkHttpClient { request ->
                request.buildResponse {
                    header("Expires", "not a valid date")
                    body(
                        when (val url = request.url.toString()) {
                            LIGHT_URL -> LIGHT_LOGO_CONTENT
                            DARK_URL -> DARK_LOGO_CONTENT
                            else -> fail("Unexpected URL $url")
                        }
                            .toResponseBody(),
                    )
                }
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        updateAppLogoUseCase.call(
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
        )

        coVerify { appLogoProviderMock.saveAppLogo(any(), "0", null) }
        coVerify { appLogoProviderMock.saveAppLogo(any(), "1", null) }
    }

    @Test
    fun `no request made if not yet expired`() = runTest {
        val appLogoProviderMock = mockk<AppLogoProvider> {
            every { isAppLogoExpired(any()) } returns false
        }
        val updateAppLogoUseCase = UpdateAppLogoUseCase(
            fileService = mockk(),
            appLogoProvider = appLogoProviderMock,
            okHttpClient = mockNoRequestOkHttpClient(),
            dispatcherProvider = testDispatcherProvider(),
        )

        updateAppLogoUseCase.call(
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
        )

        coVerify(exactly = 0) { appLogoProviderMock.saveAppLogo(any(), any(), any()) }
    }

    @Test
    fun `expiration is ignored if forced`() = runTest {
        val lightFile = File.createTempFile("logo-light", "test")
        val darkFile = File.createTempFile("logo-dark", "test")
        val fileServiceMock = mockk<FileService> {
            every { createTempFile(any(), any()) } returnsMany listOf(lightFile, darkFile)
        }
        val appLogoProviderMock = mockk<AppLogoProvider> {
            coEvery { saveAppLogo(any(), "0", any()) } answers {
                assertEquals(LIGHT_LOGO_CONTENT, firstArg<File>().readText())
            }
            coEvery { saveAppLogo(any(), "1", any()) } answers {
                assertEquals(DARK_LOGO_CONTENT, firstArg<File>().readText())
            }
            every { isAppLogoExpired(any()) } returns false
        }
        val updateAppLogoUseCase = UpdateAppLogoUseCase(
            fileService = fileServiceMock,
            appLogoProvider = appLogoProviderMock,
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("GET", request.method)
                request.buildResponse {
                    header("Expires", "Tue, 05 Aug 2025 07:28:00 GMT")
                    body(
                        when (val url = request.url.toString()) {
                            LIGHT_URL -> LIGHT_LOGO_CONTENT
                            DARK_URL -> DARK_LOGO_CONTENT
                            else -> fail("Unexpected URL $url")
                        }
                            .toResponseBody(),
                    )
                }
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        updateAppLogoUseCase.call(
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = true,
        )

        coVerify { appLogoProviderMock.saveAppLogo(lightFile, "0", Instant.parse("2025-08-05T07:28:00Z")) }
        coVerify { appLogoProviderMock.saveAppLogo(darkFile, "1", Instant.parse("2025-08-05T07:28:00Z")) }
        assertFalse(lightFile.exists())
        assertFalse(darkFile.exists())
    }

    @Test
    fun `no request made and logos cleared if URLs are null`() = runTest {
        val appLogoProviderMock = mockk<AppLogoProvider> {
            coEvery { clearAppLogo(any()) } just runs
            every { isAppLogoExpired(any()) } returns true
        }
        val updateAppLogoUseCase = UpdateAppLogoUseCase(
            fileService = mockk(),
            appLogoProvider = appLogoProviderMock,
            okHttpClient = mockNoRequestOkHttpClient(),
            dispatcherProvider = testDispatcherProvider(),
        )

        updateAppLogoUseCase.call(
            lightUrl = null,
            darkUrl = null,
            forceUpdate = false,
        )

        coVerify(exactly = 1) { appLogoProviderMock.clearAppLogo("0") }
        coVerify(exactly = 1) { appLogoProviderMock.clearAppLogo("1") }
    }

    @Test
    fun `request fails with IO exception`() = runTest {
        val appLogoProviderMock = mockk<AppLogoProvider> {
            every { isAppLogoExpired(any()) } returns true
        }
        val updateAppLogoUseCase = UpdateAppLogoUseCase(
            fileService = mockk(),
            appLogoProvider = appLogoProviderMock,
            okHttpClient = mockOkHttpClient {
                throw IOException()
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        updateAppLogoUseCase.call(
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
        )

        coVerify(exactly = 0) { appLogoProviderMock.saveAppLogo(any(), any(), any()) }
    }

    @Test
    fun `request fails with 404 not found`() = runTest {
        val fileServiceMock = mockk<FileService>()
        val appLogoProviderMock = mockk<AppLogoProvider> {
            every { isAppLogoExpired(any()) } returns true
        }
        val updateAppLogoUseCase = UpdateAppLogoUseCase(
            fileService = fileServiceMock,
            appLogoProvider = appLogoProviderMock,
            okHttpClient = mockOkHttpClient { request ->
                request.respondWith(code = 404)
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        updateAppLogoUseCase.call(
            lightUrl = LIGHT_URL,
            darkUrl = DARK_URL,
            forceUpdate = false,
        )

        coVerify(exactly = 0) { appLogoProviderMock.saveAppLogo(any(), any(), any()) }
    }

    companion object {
        private const val LIGHT_URL = "http://light-url/"
        private const val DARK_URL = "http://dark-url/"

        private const val LIGHT_LOGO_CONTENT = "light-logo"
        private const val DARK_LOGO_CONTENT = "dark-logo"
    }
}
