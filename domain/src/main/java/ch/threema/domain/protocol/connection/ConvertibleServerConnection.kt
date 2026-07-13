package ch.threema.domain.protocol.connection

import androidx.annotation.WorkerThread
import ch.threema.common.DelegateStateFlow
import ch.threema.common.stateFlowOf
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import java.util.function.Supplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = ConnectionLoggingUtil.getConnectionLogger("ConvertibleServerConnection")

/**
 * A wrapper that can handle changing connections. Every time the connection is started the used
 * connection is retrieved by calling [Supplier.get] of the [connectionSupplier].
 *
 * The [connectionSupplier] is responsible to provide a [ServerConnection] that can be used without
 * interfering with the previous connection.
 */
open class ConvertibleServerConnection(
    private val connectionSupplier: Supplier<ServerConnection>,
) : ServerConnection, ReconnectableServerConnection {
    private var connection: ServerConnection? = null
        set(value) {
            connectionStateFlow.delegate = value?.watchConnectionState() ?: stateFlowOf(ConnectionState.DISCONNECTED)
            field = value
        }

    override val isRunning: Boolean
        get() = connection?.isRunning ?: false

    private val connectionStateFlow = DelegateStateFlow(stateFlowOf(ConnectionState.DISCONNECTED))

    override fun watchConnectionState(): StateFlow<ConnectionState> = connectionStateFlow

    override val connectionState: ConnectionState
        get() = connectionStateFlow.value

    override val isNewConnectionSession: Boolean
        get() = connection?.isNewConnectionSession ?: true

    override fun disableReconnect() {
        connection?.disableReconnect()
    }

    override fun start() {
        logger.debug("Start")

        if (connection?.isRunning == true) {
            logger.warn("Connection is already running")
            return
        }

        if (!connection.let { it == null || it.connectionState == ConnectionState.DISCONNECTED }) {
            logger.warn("Connection is neither new nor disconnected. Abort connecting.")
            return
        }

        connectionSupplier.get().also { newConnection ->
            if (newConnection != connection) {
                logger.debug("Connection has changed")

                // Drop and stop old connection
                connection?.let { oldConnection ->
                    logger.debug("Stopping old connection asynchronously")
                    CoroutineScope(Dispatchers.IO).launch {
                        oldConnection.stop()
                        logger.debug("Old connection stopped")
                    }
                }

                // Register new connection
                connection = newConnection
            }
        }.start()
    }

    @WorkerThread
    @Throws(InterruptedException::class)
    override fun stop() {
        logger.debug("Stop")

        synchronized(this) {
            connection?.stop()
        }
    }

    @WorkerThread
    @Throws(InterruptedException::class)
    override fun reconnect() {
        synchronized(this) {
            logger.info("Reconnect")
            stop()
            start()
        }
    }
}
