package ch.threema.app.compose.edithistory

import androidx.lifecycle.ViewModel
import ch.threema.common.mapState
import ch.threema.common.stateFlowOf
import ch.threema.data.models.EditHistoryEntryData
import ch.threema.data.models.EditHistoryListModel
import ch.threema.data.repositories.EditHistoryRepository
import ch.threema.domain.types.MessageUid
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.InjectedParam

class EditHistoryViewModel(
    editHistoryRepository: EditHistoryRepository,
    @InjectedParam messageUid: MessageUid,
) : ViewModel() {

    // Hard reference needed to avoid being collected by the GC.
    private val editHistoryModel: EditHistoryListModel? =
        editHistoryRepository.getByMessageUid(messageUid)

    val editHistoryEntries: StateFlow<List<EditHistoryEntryData>> =
        editHistoryModel?.dataFlow?.mapState { editHistoryItems ->
            editHistoryItems ?: emptyList()
        } ?: stateFlowOf(emptyList())
}
