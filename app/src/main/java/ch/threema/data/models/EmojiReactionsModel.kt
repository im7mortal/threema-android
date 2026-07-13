package ch.threema.data.models

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.flow.MutableStateFlow

class EmojiReactionsModel(
    data: List<EmojiReactionData>,
    multiDeviceManager: MultiDeviceManager,
    taskManager: TaskManager,
) : BaseModel<List<EmojiReactionData>, Task<*, TaskCodec>>(
    modelName = "EmojiReactionModel",
    mutableData = MutableStateFlow(data),
    multiDeviceManager = multiDeviceManager,
    taskManager = taskManager,
) {
    fun addEntry(entry: EmojiReactionData) {
        if (mutableData.value?.none { it.emojiSequence == entry.emojiSequence && it.senderIdentity == entry.senderIdentity } == true) {
            mutableData.value = mutableData.value?.toMutableList()?.apply {
                add(0, entry)
            }
        }
    }

    fun removeEntry(entry: EmojiReactionData) {
        if (mutableData.value?.any { it.emojiSequence == entry.emojiSequence && it.senderIdentity == entry.senderIdentity } == true) {
            mutableData.value = mutableData.value?.toMutableList()?.apply {
                remove(entry)
            }
        }
    }

    fun clear() {
        mutableData.value = emptyList()
    }
}
