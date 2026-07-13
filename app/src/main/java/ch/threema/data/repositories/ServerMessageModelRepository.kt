package ch.threema.data.repositories

import ch.threema.common.DispatcherProvider
import ch.threema.storage.factories.ServerMessageModelFactory
import ch.threema.storage.models.ServerMessageModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class ServerMessageModelRepository(
    private val serverMessageModelFactory: ServerMessageModelFactory,
    dispatcherProvider: DispatcherProvider,
) {
    private val coroutineScope = CoroutineScope(dispatcherProvider.worker)
    private val serverMessageFlow = MutableSharedFlow<ServerMessageModel>()

    fun watchNewServerMessages(): Flow<ServerMessageModel> =
        serverMessageFlow

    fun popServerMessage(): ServerMessageModel? =
        serverMessageModelFactory.popServerMessageModel()

    fun saveServerMessage(message: ServerMessageModel) {
        serverMessageModelFactory.storeServerMessageModel(message)
        coroutineScope.launch {
            serverMessageFlow.emit(message)
        }
    }

    fun deleteServerMessageByMessage(message: String) {
        serverMessageModelFactory.delete(message)
    }
}
