package ch.threema.app

import androidx.annotation.WorkerThread
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.multidevice.PersistedMultiDeviceProperties
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.TaskCreator
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.InboundD2mMessage.DevicesInfo
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.testhelpers.MUST_NOT_BE_CALLED
import kotlinx.coroutines.flow.Flow

class TestMultiDeviceManager(
    override val isMdDisabledOrSupportsFs: Boolean = true,
    override val isMultiDeviceActive: Boolean = false,
    override val propertiesProvider: MultiDevicePropertyProvider = TestMultiDevicePropertyProvider,
    override val socketCloseListener: D2mSocketCloseListener = D2mSocketCloseListener { },
) : MultiDeviceManager {
    @WorkerThread
    override fun removeMultiDeviceLocally(serviceManager: ServiceManager) {
        MUST_NOT_BE_CALLED()
    }

    override suspend fun setDeviceLabel(deviceLabel: String) {
        MUST_NOT_BE_CALLED()
    }

    override suspend fun linkDevice(
        serviceManager: ServiceManager,
        deviceJoinOfferUri: String,
        taskCreator: TaskCreator,
    ): Flow<DeviceLinkingStatus> {
        MUST_NOT_BE_CALLED()
    }

    override suspend fun loadLinkedDevices(taskCreator: TaskCreator): Result<Map<DeviceId, DevicesInfo.AugmentedDeviceInfo>> {
        MUST_NOT_BE_CALLED()
    }

    override suspend fun setProperties(persistedProperties: PersistedMultiDeviceProperties?) {
        MUST_NOT_BE_CALLED()
    }

    override fun reconnect() {
        MUST_NOT_BE_CALLED()
    }

    override suspend fun disableForwardSecurity(
        handle: ActiveTaskCodec,
        contactService: ContactService,
        userService: UserService,
        fsMessageProcessor: ForwardSecurityMessageProcessor,
        taskCreator: TaskCreator,
    ) {
        MUST_NOT_BE_CALLED()
    }

    override fun enableForwardSecurity(serviceManager: ServiceManager) {
        MUST_NOT_BE_CALLED()
    }
}
