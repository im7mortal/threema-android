package ch.threema.app.systemupdates.updates

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.tasks.SyncPredefinedContactsTask
import ch.threema.domain.taskmanager.TaskManager
import org.koin.core.component.inject

class SystemUpdateToVersion127 : SystemUpdate {
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val taskManager: TaskManager by inject()

    override fun run() {
        if (!multiDeviceManager.isMultiDeviceActive) {
            return
        }

        taskManager.schedule(SyncPredefinedContactsTask())
    }

    override val version = 127

    override fun getDescription() =
        "schedule a task that syncs the verification levels of predefined contacts"
}
