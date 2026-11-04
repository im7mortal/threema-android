package ch.threema.app.usecases.conversation

import android.content.Context
import android.text.format.DateUtils
import ch.threema.app.R
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.app.services.MessageService
import ch.threema.app.utils.FileHandlingZipOutputStream
import ch.threema.app.utils.FileUtil
import ch.threema.app.utils.GeoLocationUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.deleteOrThrow
import ch.threema.common.forEachReversed
import ch.threema.common.getUniqueFile
import ch.threema.common.toHMMSS
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ExportConversationToFileUseCase")

class ExportConversationToFileUseCase(
    private val appContext: Context,
    private val fileService: FileService,
    private val messageService: MessageService,
    private val contactModelRepository: ContactModelRepository,
    private val preferenceService: PreferenceService,
    private val appDirectoryProvider: AppDirectoryProvider,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(
        conversationModel: ConversationModel,
        password: String,
        includeMedia: Boolean,
    ): Result = withContext(dispatcherProvider.io) {
        exportToZip(
            messages = messageService.getMessagesForReceiver(conversationModel.messageReceiver),
            prefixWithSenderName = !conversationModel.isGroupConversation,
            outputFile = appDirectoryProvider.shareDirectory.getUniqueFile(fileName = ZIP_FILE_NAME),
            password = password,
            includeMedia = includeMedia,
        )
    }

    private fun CoroutineScope.exportToZip(
        messages: List<MessageModel>,
        prefixWithSenderName: Boolean,
        outputFile: File,
        password: String,
        includeMedia: Boolean,
    ): Result =
        try {
            FileHandlingZipOutputStream.initializeZipOutputStream(outputFile, password).use { zipOutputStream ->
                addMessagesToZip(
                    messages = messages,
                    prefixWithSenderName = prefixWithSenderName,
                    zipOutputStream = zipOutputStream,
                )
                if (includeMedia) {
                    addFilesToZip(
                        messageModels = messages,
                        zipOutputStream = zipOutputStream,
                    )
                }
            }
            Result.Success(outputFile)
        } catch (e: CancellationException) {
            if (!outputFile.delete()) {
                logger.warn("Failed to delete export file")
            }
            throw e
        } catch (e: Exception) {
            logger.error("Failed to export conversation", e)
            outputFile.deleteOrThrow()
            Result.Failure
        }

    private fun CoroutineScope.addMessagesToZip(
        messages: List<MessageModel>,
        prefixWithSenderName: Boolean,
        zipOutputStream: FileHandlingZipOutputStream,
    ) {
        val messagesText = buildString {
            messages.forEachReversed { message ->
                ensureActive()
                if (!message.isStatusMessage) {
                    addMessageLine(
                        message = message,
                        prefixWithSenderName = prefixWithSenderName,
                    )
                }
            }
        }
        zipOutputStream.addFileFromInputStream(
            inputStream = messagesText.byteInputStream(),
            filenameInZip = MESSAGES_FILE_NAME,
            compress = true,
        )
    }

    private fun StringBuilder.addMessageLine(
        message: MessageModel,
        prefixWithSenderName: Boolean,
    ) {
        var messageLine = ""

        if (prefixWithSenderName) {
            message.getSenderName()?.let { senderName ->
                messageLine = "$senderName: "
            }
        }

        messageLine += messageService.getMessageString(message, 0).rawMessage

        when (message.type) {
            MessageType.FILE -> " <${message.getFilename()}>"
            MessageType.LOCATION -> " <${GeoLocationUtil.getLocationUri(message)}>"
            MessageType.VOIP_STATUS -> message.voipStatusData?.durationInSeconds?.seconds?.let { duration ->
                " <${duration.toHMMSS()}>"
            }
            else -> null
        }
            ?.let { extra ->
                messageLine += extra
            }

        if (!messageLine.isEmpty()) {
            append("[")
            append(message.getFormattedDate())
            append("] ")
            append(messageLine)
            append("\n")
        }
    }

    private fun MessageModel.getSenderName(): String? =
        if (isOutbox) {
            appContext.getString(R.string.me_myself_and_i)
        } else {
            identity
                ?.let(contactModelRepository::getByIdentity)
                ?.data
                ?.getDisplayName(
                    contactNameFormat = preferenceService.getContactNameFormat(),
                    nicknameHasPrefix = true,
                )
        }

    private fun MessageModel.getFilename(): String {
        val fileDataModel = fileData
        return if (fileDataModel.fileName.isNullOrEmpty()) {
            FileUtil.getDefaultFilename(fileDataModel.getMimeType())
        } else {
            "${apiMessageId ?: id}-${fileDataModel.fileName}"
        }
    }

    private fun MessageModel.getFormattedDate(): String =
        DateUtils.formatDateTime(
            appContext,
            postedAt!!.toEpochMilli(),
            DateUtils.FORMAT_ABBREV_ALL or
                DateUtils.FORMAT_SHOW_YEAR or
                DateUtils.FORMAT_SHOW_DATE or
                DateUtils.FORMAT_NUMERIC_DATE or
                DateUtils.FORMAT_SHOW_TIME,
        )

    private fun CoroutineScope.addFilesToZip(
        messageModels: List<MessageModel>,
        zipOutputStream: FileHandlingZipOutputStream,
    ) {
        messageModels
            .filter { message ->
                message.type == MessageType.FILE && message.fileData.isDownloaded
            }
            .forEach { message ->
                ensureActive()
                addFileToZip(message, zipOutputStream)
            }
    }

    private fun addFileToZip(
        message: MessageModel,
        zipOutputStream: FileHandlingZipOutputStream,
    ) {
        try {
            zipOutputStream.writeMediaFile(
                messageUid = message.uid ?: return,
                filename = message.getFilename(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // if an error occurs for a media file, we just skip it
            logger.warn("Failed to include media file", e)
        }
    }

    private fun FileHandlingZipOutputStream.writeMediaFile(messageUid: MessageUid, filename: String) {
        val mediaInputStream = fileService.decryptMessageFileToStream(messageUid)
            ?: fileService.decryptedMessageThumbnailToStream(messageUid)
        mediaInputStream?.use { inputStream ->
            addFileFromInputStream(
                inputStream = inputStream,
                filenameInZip = filename,
                compress = false,
            )
        }
    }

    sealed interface Result {
        data class Success(val file: File) : Result
        data object Failure : Result
    }

    companion object {
        private const val ZIP_FILE_NAME = "threema-chat.zip"
        private const val MESSAGES_FILE_NAME = "messages.txt"
    }
}
