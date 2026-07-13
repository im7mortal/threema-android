package ch.threema.app.storagemanagement.usecases

import android.annotation.SuppressLint
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.services.MessageService
import ch.threema.common.ByteSize
import ch.threema.common.DispatcherProvider
import ch.threema.common.bytes
import ch.threema.common.getTotalDirectorySize
import kotlinx.coroutines.withContext

class GetStorageSizeUseCase(
    private val appDirectoryProvider: AppDirectoryProvider,
    private val messageService: MessageService,
    private val dispatcherProvider: DispatcherProvider,
) {
    @SuppressLint("UsableSpace")
    suspend fun call(): Result = withContext(dispatcherProvider.io) {
        @Suppress("DEPRECATION")
        Result(
            totalSpace = appDirectoryProvider.userFilesDirectory.totalSpace.bytes,
            freeSpace = appDirectoryProvider.userFilesDirectory.usableSpace.bytes,
            usedSpace = appDirectoryProvider.userFilesDirectory.getTotalDirectorySize() +
                appDirectoryProvider.legacyUserFilesDirectory.getTotalDirectorySize(),
            messageCount = messageService.getTotalMessageCount(),
        )
    }

    data class Result(
        val totalSpace: ByteSize,
        val freeSpace: ByteSize,
        val usedSpace: ByteSize,
        val messageCount: Long,
    )
}
