package ch.threema.app.groupmanagement

import ch.threema.KoinTestRule
import ch.threema.app.TestMultiDeviceManager
import ch.threema.app.di.modules.sessionScopedModule
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.common.stateFlowOf
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.taskmanager.TaskManager
import org.junit.Rule
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.module.Module

enum class SetupConfig {
    MULTI_DEVICE_ENABLED,
    MULTI_DEVICE_DISABLED,
}

enum class ReflectionExpectation(val setupConfig: SetupConfig) {
    /**
     * In case multi device is enabled and the reflection is expected to succeed.
     */
    REFLECTION_SUCCESS(SetupConfig.MULTI_DEVICE_ENABLED),

    /**
     * In case multi device is enabled and the reflection is expected to fail due to an unfulfilled
     * precondition.
     */
    REFLECTION_FAIL(SetupConfig.MULTI_DEVICE_ENABLED),

    /**
     * In case multi device is disabled and the reflection is expected to be skipped.
     */
    REFLECTION_SKIPPED(SetupConfig.MULTI_DEVICE_DISABLED),
}

abstract class GroupFlowTest : KoinComponent {
    protected val serviceManager by lazy { ServiceManager.require() }

    protected val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    protected val groupModelRepository by lazy { serviceManager.modelRepositories.groups }
    private val globalEventBuses: GlobalEventBuses by inject()

    protected val testMultiDeviceManagerEnabled by lazy {
        TestMultiDeviceManager(
            isMdDisabledOrSupportsFs = false,
            isMultiDeviceActive = true,
        )
    }

    protected val testMultiDeviceManagerDisabled by lazy {
        TestMultiDeviceManager(
            isMdDisabledOrSupportsFs = true,
            isMultiDeviceActive = false,
        )
    }

    protected fun getGroupFlowDispatcher(
        setupConfig: SetupConfig,
        taskManager: TaskManager,
        connection: ServerConnection = ConnectionLoggedIn,
    ) = GroupFlowDispatcher(
        serviceManager.modelRepositories.contacts,
        serviceManager.modelRepositories.groups,
        serviceManager.contactService,
        serviceManager.groupService,
        serviceManager.groupCallManager,
        serviceManager.userService,
        serviceManager.contactStore,
        serviceManager.identityStore,
        serviceManager.forwardSecurityMessageProcessor,
        serviceManager.nonceFactory,
        serviceManager.preferenceService,
        serviceManager.synchronizedSettingsService,
        when (setupConfig) {
            SetupConfig.MULTI_DEVICE_ENABLED -> testMultiDeviceManagerEnabled
            SetupConfig.MULTI_DEVICE_DISABLED -> testMultiDeviceManagerDisabled
        },
        serviceManager.groupProfilePictureUploader,
        serviceManager.apiConnector,
        serviceManager.fileService,
        serviceManager.databaseService,
        taskManager,
        connection,
        serviceManager.identityBlockedSteps,
        globalEventBuses,
    )

    /**
     * This module is added to the koin modules so that specific dependencies can be provided.
     */
    protected open fun getInstrumentationTestModule(): Module? = null

    @get:Rule
    val koinTestRule = KoinTestRule(
        modules = listOfNotNull(sessionScopedModule, getInstrumentationTestModule()),
    )

    data object ConnectionDisconnected : ServerConnection {

        override val isRunning: Boolean = false

        override val connectionState: ConnectionState = ConnectionState.DISCONNECTED

        override fun watchConnectionState() = stateFlowOf(ConnectionState.DISCONNECTED)

        override val isNewConnectionSession: Boolean = false

        override fun disableReconnect() {}

        override fun start() {}

        override fun stop() {}
    }

    data object ConnectionLoggedIn : ServerConnection {

        override val isRunning: Boolean = true

        override val connectionState: ConnectionState = ConnectionState.LOGGED_IN

        override fun watchConnectionState() = stateFlowOf(ConnectionState.DISCONNECTED)

        override val isNewConnectionSession: Boolean = true

        override fun disableReconnect() {}

        override fun start() {}

        override fun stop() {}
    }
}
