package ch.threema.domain.protocol.connection.csp.socket

import java.net.InetAddress
import java.net.UnknownHostException

class HostResolver {
    @Throws(UnknownHostException::class)
    fun getAllByName(name: String): Array<InetAddress> =
        InetAddress.getAllByName(name)
            .ifEmpty {
                throw UnknownHostException()
            }
}
