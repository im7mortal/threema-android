package ch.threema.app

import ch.threema.domain.taskmanager.QueueSendCompleteListener
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TestTaskManager(
    private val taskCodec: TaskCodec,
) : TaskManager {
    private val taskQueue = Channel<QueueElement<Any>>()

    private data class QueueElement<T>(
        val task: Task<T, TaskCodec>,
        val deferred: CompletableDeferred<T>,
    )

    init {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val (task, deferred) = taskQueue.receive()
                try {
                    deferred.complete(task.invoke(taskCodec))
                } catch (e: Throwable) {
                    deferred.completeExceptionally(e)
                }
            }
        }
    }

    override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
        val deferred = CompletableDeferred<R>()
        runBlocking {
            @Suppress("UNCHECKED_CAST")
            taskQueue.send(QueueElement(task, deferred) as QueueElement<Any>)
        }
        return deferred
    }

    override fun hasPendingTasks() = false

    override fun addQueueSendCompleteListener(listener: QueueSendCompleteListener) {
        // Nothing to do
    }

    override fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener) {
        // Nothing to do
    }
}
