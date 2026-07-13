package ch.threema.app.connection

import android.os.PowerManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.ServerAddressProviderService
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.connection.csp.CspConnection
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.d2m.D2mConnection
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.TaskManager
import io.mockk.every
import io.mockk.mockk
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import okhttp3.OkHttpClient

class CspD2mDualConnectionSupplierTest {
    @Test
    fun testMdInactive() {
        val connectionSupplier = createSupplier(MdActiveHandle())

        val connection = connectionSupplier.get()
        assertIs<CspConnection>(connection)
        // subsequent call must return the same instance
        assertSame(connection, connectionSupplier.get())
    }

    @Test
    fun testMdActive() {
        val connectionSupplier = createSupplier(MdActiveHandle(true))

        val connection = connectionSupplier.get()
        assertIs<D2mConnection>(connection)
        // subsequent call must return the same instance
        assertSame(connection, connectionSupplier.get())
    }

    @Test
    fun testMdInactiveToggleActive() {
        val handle = MdActiveHandle(false)
        val connectionSupplier = createSupplier(handle)

        assertIs<CspConnection>(connectionSupplier.get())
        handle.isMdActive = true
        assertIs<D2mConnection>(connectionSupplier.get())
        handle.isMdActive = false
        assertIs<CspConnection>(connectionSupplier.get())
    }

    @Test
    fun testMdActiveToggleInactive() {
        val handle = MdActiveHandle(true)
        val connectionSupplier = createSupplier(handle)

        assertIs<D2mConnection>(connectionSupplier.get())
        handle.isMdActive = false
        assertIs<CspConnection>(connectionSupplier.get())
        handle.isMdActive = true
        assertIs<D2mConnection>(connectionSupplier.get())
    }

    private fun createSupplier(mdActiveHandle: MdActiveHandle): Supplier<ServerConnection> =
        CspD2mDualConnectionSupplier(
            powerManager = mockk<PowerManager>(),
            multiDeviceManager = mockk<MultiDeviceManager> {
                every { isMultiDeviceActive } answers { mdActiveHandle.isMdActive }
                every { propertiesProvider } returns mockk<MultiDevicePropertyProvider>()
                every { socketCloseListener } returns mockk<D2mSocketCloseListener>()
            },
            incomingMessageProcessor = mockk<IncomingMessageProcessor>(),
            taskManager = mockk<TaskManager>(),
            deviceCookieManager = mockk<DeviceCookieManager>(),
            serverAddressProviderService = mockk<ServerAddressProviderService> {
                every { serverAddressProvider } returns mockk<ServerAddressProvider>()
            },
            identityStore = mockk<IdentityStore>(),
            version = Version(),
            okHttpClient = mockk<OkHttpClient>(),
            appStartupMonitor = mockk<AppStartupMonitor>(),
            isTestBuild = false,
        )

    class MdActiveHandle(var isMdActive: Boolean = false)
}
