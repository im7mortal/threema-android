package ch.threema.app

import ch.threema.domain.protocol.D2mProtocolDefines
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.D2mProtocolVersion
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties

object TestMultiDevicePropertyProvider : MultiDevicePropertyProvider {
    override fun get() = MultiDeviceProperties(
        registrationTime = 0u,
        mediatorDeviceId = DeviceId(0u),
        cspDeviceId = DeviceId(0u),
        keys = MultiDeviceKeys(ByteArray(D2mProtocolDefines.DGK_LENGTH_BYTES)),
        deviceInfo = D2dMessage.DeviceInfo(
            platform = D2dMessage.DeviceInfo.Platform.ANDROID,
            platformDetails = "",
            appVersion = "",
            label = "",
        ),
        protocolVersion = D2mProtocolVersion(UInt.MIN_VALUE, UInt.MAX_VALUE),
        serverInfoListener = {},
    )
}
