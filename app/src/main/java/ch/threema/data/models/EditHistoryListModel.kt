package ch.threema.data.models

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow

class EditHistoryListModel(
    data: List<EditHistoryEntryData>,
    multiDeviceManager: MultiDeviceManager,
    taskManager: TaskManager,
) : BaseModel<List<EditHistoryEntryData>, Task<*, TaskCodec>>(
    modelName = "EditHistoryListModel",
    mutableData = MutableStateFlow(data),
    multiDeviceManager = multiDeviceManager,
    taskManager = taskManager,
) {

    /**
     * Adds the given [entry] to the list of edit entries.
     *
     * @throws ModelDeletedException if model is deleted
     */
    @Synchronized
    fun addEntry(entry: EditHistoryEntryData) {
        val currentEntries = ensureNotDeleted("addEntry")
        if (!currentEntries.contains(entry)) {
            mutableData.value = listOf(entry) + currentEntries
        }
    }

    /**
     * @throws ModelDeletedException if model is deleted
     */
    @Synchronized
    fun delete() {
        ensureNotDeleted("delete")
        mutableData.value = null
    }
}
