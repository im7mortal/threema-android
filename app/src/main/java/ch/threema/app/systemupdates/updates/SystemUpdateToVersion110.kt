package ch.threema.app.systemupdates.updates

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.tasks.SyncFormerlyOrphanedGroupsTask
import ch.threema.domain.taskmanager.TaskManager
import org.koin.core.component.inject

/**
 * schedule a task that syncs user state for all groups, in particular formerly orphaned ones
 */
class SystemUpdateToVersion110 : SystemUpdate {
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val taskManager: TaskManager by inject()

    override fun run() {
        if (!multiDeviceManager.isMultiDeviceActive) {
            return
        }

        taskManager.schedule(SyncFormerlyOrphanedGroupsTask())
    }

    override val version = 110
}
