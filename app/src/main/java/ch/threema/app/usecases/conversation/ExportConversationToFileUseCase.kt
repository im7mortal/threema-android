package ch.threema.app.usecases.conversation

import ch.threema.app.backuprestore.ExportConversationService
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.common.DispatcherProvider
import ch.threema.common.getUniqueFile
import ch.threema.storage.models.ConversationModel
import java.io.File
import kotlinx.coroutines.withContext

class ExportConversationToFileUseCase(
    private val dispatcherProvider: DispatcherProvider,
    private val exportConversationService: ExportConversationService,
    private val appDirectoryProvider: AppDirectoryProvider,
) {

    suspend fun call(
        conversationModel: ConversationModel,
        password: String,
        includeMedia: Boolean,
    ): Result = withContext(dispatcherProvider.io) {
        val conversationFile = appDirectoryProvider.shareDirectory.getUniqueFile(fileName = "threema-chat.zip")
        val exportResult: ExportConversationService.ExportResult = exportConversationService.exportToZip(
            /* conversationModel = */
            conversationModel,
            /* outputFile = */
            conversationFile,
            /* password = */
            password,
            /* includeMedia = */
            includeMedia,
        )
        when (exportResult) {
            ExportConversationService.ExportResult.SUCCESS -> Result.Success(conversationFile)
            ExportConversationService.ExportResult.FAILURE -> Result.Failure
            ExportConversationService.ExportResult.CANCELLED -> Result.Cancelled
        }
    }

    sealed interface Result {
        data class Success(val file: File) : Result
        data object Failure : Result
        data object Cancelled : Result
    }
}
