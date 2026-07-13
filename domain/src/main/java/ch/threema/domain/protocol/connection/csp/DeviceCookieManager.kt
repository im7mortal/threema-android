package ch.threema.domain.protocol.connection.csp

interface DeviceCookieManager {
    /**
     * Obtain an existing or new device cookie.
     *
     * @return the device cookie (16 bytes)
     */
    fun getOrCreateDeviceCookie(): ByteArray

    /**
     * Inform the manager that a device cookie change indication has been received from the server.
     */
    fun onChangeIndicationReceived()
}
