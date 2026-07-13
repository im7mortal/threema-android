package ch.threema.app.tasks.archive.recovery

import ch.threema.app.tasks.archive.recovery.handlers.GroupCreateTaskRecoveryHandler
import ch.threema.app.tasks.archive.recovery.handlers.GroupUpdateTaskRecoveryHandler
import ch.threema.app.tasks.archive.recovery.handlers.ReflectNotificationTriggerPolicyOverrideUpdateRecoveryHandler
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec

class TaskRecoveryManagerImpl : TaskRecoveryManager {
    private val taskRecoveryHandlers: List<TaskRecoveryHandler> by lazy {
        listOf(
            ReflectNotificationTriggerPolicyOverrideUpdateRecoveryHandler,
            GroupCreateTaskRecoveryHandler,
            GroupUpdateTaskRecoveryHandler,
        )
    }

    override fun recoverTask(encodedTask: String): Task<*, TaskCodec>? =
        taskRecoveryHandlers.firstNotNullOfOrNull { taskRecoveryHandler -> taskRecoveryHandler.tryRecovery(encodedTask) }
}
