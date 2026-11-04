package ch.threema.app.services

import ch.threema.base.SessionScoped
import kotlinx.coroutines.flow.StateFlow

/**
 * The Lifetime Service is responsible for opening and closing a connection to the Threema
 * chat server. Every part of the code that needs a connection can acquire a "connection slot".
 * If there are 1 or more slots open, then a connection is kept open, otherwise it is closed.
 *
 * In some situations we want to stop the connection even if the counter is >0,
 * for example when the device is going to deep sleep. For this, the [.pause]
 * and [.unpause] methods can be used.
 *
 * If you want to acquire a connection that should not be pauseable (e.g. for the Threema Push
 * service), then set the `unpauseable` flag (or use [acquireUnpauseableConnection])
 * when acquiring the connection.
 */
@SessionScoped
interface LifetimeService {

    typealias SourceTag = String

    /**
     * Acquire a connection. If not already connected, the connection will be started immediately,
     * and kept active until releaseConnection() is called. A call to acquireConnection() must always
     * be balanced with a call to releaseConnection() with the same tag.
     *
     * @param sourceTag   identifier of the connection slot
     * @param unpauseable if set to true, then the connection will not be paused in deep sleep
     */
    fun acquireConnection(sourceTag: SourceTag, unpauseable: Boolean)

    /**
     * Shortcut for [.acquireConnection] with unpauseable=false.
     */
    fun acquireConnection(sourceTag: SourceTag) {
        acquireConnection(sourceTag, false)
    }

    /**
     * Shortcut for [.acquireConnection] with unpauseable=true.
     */
    fun acquireUnpauseableConnection(sourceTag: SourceTag) {
        acquireConnection(sourceTag, true)
    }

    /**
     * Release the connection. If no other callers have acquired the connection anymore, it will
     * be closed.
     *
     * @param sourceTag identifier of the connection slot
     */
    fun releaseConnection(sourceTag: SourceTag)

    /**
     * Release the connection, but keep it active ("linger") for the given amount of milliseconds
     *
     * @param sourceTag identifier of the connection slot
     * @param timeoutMs time in milliseconds that the connection should linger
     */
    fun releaseConnectionLinger(sourceTag: SourceTag, timeoutMs: Long)

    /**
     * If the connection is not active but there are connection slots registered
     * and the app is not in a special mode, establish a connection.
     * If the connection is already active, or if no connection slots are registered, nothing will happen.
     *
     * Note: Sometimes when acquiring a connection, the connection cannot be established
     * (e.g. if the identity is not yet set up). In this case, this method can be called
     * to trigger a reconnect attempt.
     */
    fun startConnectionIfNeeded()

    /**
     * Alarm elapsed (for use by AlarmManagerBroadcastReceiver)
     */
    fun handleAlarm(requestCode: Int)

    /**
     * Return whether a connection is active (connected).
     */
    val isActive: Boolean

    fun watchIsActive(): StateFlow<Boolean>

    /**
     * Pause (close) the connection, even if the slotCount is >0.
     */
    fun pause()

    /**
     * Restore (reconnect) the connection if it was previously paused.
     */
    fun unpause()

    fun runAfterNextConnectionStop(runnable: Runnable)

    fun destroy()
}
