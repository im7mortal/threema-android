package ch.threema.app.connection

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.SessionScoped
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.IdentityProvider
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.connection.data.leBytes

private val logger = getThreemaLogger("ServerConnectionManager")

@SessionScoped
class ServerConnectionManager(
    val connection: ServerConnection,
    private val identityProvider: IdentityProvider,
    private val multiDeviceManager: MultiDeviceManager,
) {
    /**
     * Start the server connection. Do not call this directly; use the LifetimeService!
     */
    @Throws(NoIdentityException::class)
    fun startConnection() {
        if (!identityProvider.hasIdentity()) {
            throw NoIdentityException()
        }
        if (multiDeviceManager.isMultiDeviceActive) {
            val properties = multiDeviceManager.propertiesProvider.get()
            logger.info("Starting connection (mediatorDeviceId = {})", properties.mediatorDeviceId.leBytes().toHexString())
        } else {
            logger.info("Starting connection")
        }
        connection.start()
    }

    class NoIdentityException : Exception()

    /**
     * Stop the connection. Do not call this directly; use the LifetimeService!
     */
    @Throws(InterruptedException::class)
    fun stopConnection() {
        if (!connection.isRunning) {
            return
        }
        logger.info("Stopping connection")
        try {
            connection.stop()
        } catch (e: InterruptedException) {
            logger.error("Interrupted while stopping connection")
            Thread.currentThread().interrupt()
            throw e
        }
    }
}
