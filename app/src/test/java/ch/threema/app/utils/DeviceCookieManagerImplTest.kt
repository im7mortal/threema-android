package ch.threema.app.utils

import ch.threema.app.R
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.data.repositories.ServerMessageModelRepository
import ch.threema.test.TestContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals

class DeviceCookieManagerImplTest {
    @Test
    fun `device cookie is generated`() {
        val deviceCookie = ByteArray(16) { it.toByte() }
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore>(relaxed = true) {
            every { getBytes(TestContext.getString(R.string.preferences__device_cookie)) } returns null
        }
        val deviceCookieManager = DeviceCookieManagerImpl(
            appContext = TestContext.create(),
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            serverMessageModelRepository = mockk(),
            secureRandom = mockk {
                every { nextBytes(any()) } answers { deviceCookie.copyInto(firstArg<ByteArray>()) }
            },
        )

        assertContentEquals(
            deviceCookie,
            deviceCookieManager.getOrCreateDeviceCookie(),
        )
        verify {
            encryptedPreferenceStoreMock.save(
                TestContext.getString(R.string.preferences__device_cookie),
                match<ByteArray> {
                    it.contentEquals(deviceCookie)
                },
            )
        }
    }

    @Test
    fun `device cookie is restored`() {
        val deviceCookie = ByteArray(16) { it.toByte() }
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore> {
            every { getBytes(TestContext.getString(R.string.preferences__device_cookie)) } returns deviceCookie
        }
        val deviceCookieManager = DeviceCookieManagerImpl(
            appContext = TestContext.create(),
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            serverMessageModelRepository = mockk(),
            secureRandom = mockk(),
        )

        assertContentEquals(
            deviceCookie,
            deviceCookieManager.getOrCreateDeviceCookie(),
        )
        verify(exactly = 0) {
            encryptedPreferenceStoreMock.save(
                TestContext.getString(R.string.preferences__device_cookie),
                any<ByteArray>(),
            )
        }
    }

    @Test
    fun `change indication results in server message`() {
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore>(relaxed = true) {
            every { getBytes(TestContext.getString(R.string.preferences__device_cookie)) } returns ByteArray(16)
        }
        val serverMessageModelRepositoryMock = mockk<ServerMessageModelRepository>(relaxed = true)
        val deviceCookieManager = DeviceCookieManagerImpl(
            appContext = TestContext.create(),
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            serverMessageModelRepository = serverMessageModelRepositoryMock,
            secureRandom = mockk(),
        )

        deviceCookieManager.getOrCreateDeviceCookie()
        deviceCookieManager.onChangeIndicationReceived()

        verify(exactly = 1) {
            serverMessageModelRepositoryMock.saveServerMessage(any())
        }
    }

    @Test
    fun `change indication is ignored after newly generated device cookie`() {
        val encryptedPreferenceStoreMock = mockk<EncryptedPreferenceStore>(relaxed = true) {
            every { getBytes(TestContext.getString(R.string.preferences__device_cookie)) } returns null
        }
        val serverMessageModelRepositoryMock = mockk<ServerMessageModelRepository>(relaxed = true)
        val deviceCookieManager = DeviceCookieManagerImpl(
            appContext = TestContext.create(),
            encryptedPreferenceStore = encryptedPreferenceStoreMock,
            serverMessageModelRepository = serverMessageModelRepositoryMock,
            secureRandom = mockk(relaxed = true),
        )

        deviceCookieManager.getOrCreateDeviceCookie()
        deviceCookieManager.onChangeIndicationReceived()

        // The first indication after a cookie is generated is ignored
        verify(exactly = 0) {
            serverMessageModelRepositoryMock.saveServerMessage(any())
        }

        deviceCookieManager.onChangeIndicationReceived()

        // The next indication after that is processed normally
        verify(exactly = 1) {
            serverMessageModelRepositoryMock.saveServerMessage(any())
        }
    }
}
