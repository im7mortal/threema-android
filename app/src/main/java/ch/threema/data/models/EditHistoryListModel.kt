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
    fun addEntry(entry: EditHistoryEntryData) {
        if (mutableData.value?.none { it == entry } == true) {
            mutableData.value = mutableData.value?.toMutableList()?.apply { add(0, entry) }
        }
    }

    fun clear() {
        mutableData.value = emptyList()
    }
}
